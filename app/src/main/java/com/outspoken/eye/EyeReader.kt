package com.outspoken.eye

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.outspoken.log.AppLog
import com.outspoken.log.EyeSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EyeReader(private val onSample: (EyeSample) -> Unit = {}) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(options().build())

    // ML Kit advises against contours and classification together for real-time use, so the
    // outline detector only runs while the debug dots are on screen.
    private val dotsDetector = FaceDetection.getClient(
        options().setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).build()
    )

    /** Draws eye outlines on the debug screen. Slower, so off during normal use. */
    @Volatile
    var dotsOn = false

    private val _samples = MutableStateFlow<EyeSample?>(null)
    val samples: StateFlow<EyeSample?> = _samples.asStateFlow()

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val fpsMeter = FpsMeter()
    private val summary = EyeSummary()
    private var failures = 0
    private var detectTotalMs = 0L
    private var detectFrames = 0
    private var lastDetectLogMs = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        // Stamped before detection, on the same clock as the scanner, so detector latency does
        // not stretch blinks.
        val timeMs = SystemClock.elapsedRealtime()
        val rotation = image.imageInfo.rotationDegrees
        val uprightWidth = if (rotation % 180 == 0) image.width else image.height
        val uprightHeight = if (rotation % 180 == 0) image.height else image.width
        val withDots = dotsOn
        val input = InputImage.fromMediaImage(media, rotation)
        (if (withDots) dotsDetector else detector).process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val dots = if (withDots) face?.dots(uprightWidth, uprightHeight) else null
                val sample = face?.toSample(timeMs, dots) ?: EyeSample(timeMs, faceFound = false)
                _samples.value = sample
                onSample(sample)
                _fps.value = fpsMeter.onFrame(timeMs)
                summary.add(sample)?.let { AppLog.write("eyes", it) }
                noteDetectTime(timeMs, uprightWidth, uprightHeight, face, withDots)
            }
            .addOnFailureListener { error ->
                failures++
                if (failures == 1 || failures % 100 == 0) {
                    AppLog.write("eyes", "face detection failed ($failures so far): $error")
                }
            }
            .addOnCompleteListener { image.close() }
    }

    fun close() {
        detector.close()
        dotsDetector.close()
    }

    private fun noteDetectTime(startMs: Long, width: Int, height: Int, face: Face?, withDots: Boolean) {
        val now = SystemClock.elapsedRealtime()
        detectTotalMs += now - startMs
        detectFrames++
        if (now - lastDetectLogMs < DETECT_LOG_EVERY_MS) return
        val faceWidth = face?.boundingBox?.width()?.let { "$it px" } ?: "-"
        AppLog.write(
            "eyes",
            "detect ${detectTotalMs / detectFrames} ms avg, image ${width}x$height, face width $faceWidth, dots ${if (withDots) "on" else "off"}",
        )
        lastDetectLogMs = now
        detectTotalMs = 0
        detectFrames = 0
    }

    private fun Face.toSample(timeMs: Long, dots: FaceDots?) = EyeSample(
        timeMs = timeMs,
        faceFound = true,
        leftOpen = leftEyeOpenProbability,
        rightOpen = rightEyeOpenProbability,
        yawDeg = headEulerAngleY,
        pitchDeg = headEulerAngleX,
        dots = dots,
    )

    private fun Face.dots(width: Int, height: Int): FaceDots? {
        val left = getContour(FaceContour.LEFT_EYE)?.points?.map { Dot(it.x, it.y) } ?: return null
        val right = getContour(FaceContour.RIGHT_EYE)?.points?.map { Dot(it.x, it.y) } ?: return null
        fun List<Dot>.scaled() = map { Dot(it.x / width, it.y / height) }
        return FaceDots(
            leftEye = left.scaled(),
            rightEye = right.scaled(),
            imageAspect = width.toFloat() / height,
            leftShape = eyeShape(left),
            rightShape = eyeShape(right),
        )
    }

    private companion object {
        const val DETECT_LOG_EVERY_MS = 5_000L

        fun options() = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
    }
}
