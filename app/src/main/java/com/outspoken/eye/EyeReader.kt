package com.outspoken.eye

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.outspoken.log.AppLog
import com.outspoken.log.EyeSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EyeReader(private val onSample: (EyeSample) -> Unit = {}) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    private val _samples = MutableStateFlow<EyeSample?>(null)
    val samples: StateFlow<EyeSample?> = _samples.asStateFlow()

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val fpsMeter = FpsMeter()
    private val summary = EyeSummary()
    private var failures = 0

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
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val sample = face?.toSample(timeMs) ?: EyeSample(timeMs, faceFound = false)
                _samples.value = sample
                onSample(sample)
                _fps.value = fpsMeter.onFrame(timeMs)
                summary.add(sample)?.let { AppLog.write("eyes", it) }
            }
            .addOnFailureListener { error ->
                failures++
                if (failures == 1 || failures % 100 == 0) {
                    AppLog.write("eyes", "face detection failed ($failures so far): $error")
                }
            }
            .addOnCompleteListener { image.close() }
    }

    fun close() = detector.close()

    private fun Face.toSample(timeMs: Long) = EyeSample(
        timeMs = timeMs,
        faceFound = true,
        leftOpen = leftEyeOpenProbability,
        rightOpen = rightEyeOpenProbability,
        yawDeg = headEulerAngleY,
        pitchDeg = headEulerAngleX,
    )
}
