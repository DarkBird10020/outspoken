package com.outspoken.eye

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.outspoken.log.AppLog
import com.outspoken.log.EyeSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera frames in, eye samples out, using MediaPipe Face Landmarker: 478 face points including
 * both irises, plus a blink score and gaze scores per eye. [onSample] runs on the main thread.
 */
class EyeReader(context: Context, private val onSample: (EyeSample) -> Unit) : ImageAnalysis.Analyzer {

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val landmarker: FaceLandmarker? = create(context.applicationContext)

    /** Fills [EyeSample.dots] for the debug screen. */
    @Volatile
    var dotsOn = false

    private val _samples = MutableStateFlow<EyeSample?>(null)
    val samples: StateFlow<EyeSample?> = _samples.asStateFlow()

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val fpsMeter = FpsMeter()
    private val summary = EyeSummary()
    private var lastFrameMs = 0L
    private var detectTotalMs = 0L
    private var detectFrames = 0
    private var lastDetectLogMs = 0L

    override fun analyze(image: ImageProxy) {
        val model = landmarker
        if (model == null) {
            image.close()
            return
        }
        // MediaPipe needs strictly rising timestamps. They share the scanner's clock.
        val timeMs = maxOf(SystemClock.elapsedRealtime(), lastFrameMs + 1)
        lastFrameMs = timeMs
        val rotation = image.imageInfo.rotationDegrees
        val frame = image.toBitmap()
        image.close()
        val upright = if (rotation == 0) {
            frame
        } else {
            Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, false)
        }
        model.detectAsync(BitmapImageBuilder(upright).build(), timeMs)
    }

    fun close() {
        landmarker?.close()
    }

    private fun create(context: Context): FaceLandmarker? {
        for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(delegate).build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumFaces(1)
                    .setOutputFaceBlendshapes(true)
                    .setResultListener { result: FaceLandmarkerResult, input: MPImage -> onResult(result, input.width, input.height) }
                    .setErrorListener { error: RuntimeException -> AppLog.write("eyes", "face landmarker error: $error") }
                    .build()
                return FaceLandmarker.createFromOptions(context, options).also {
                    AppLog.write("eyes", "face landmarker ready on $delegate")
                }
            } catch (e: Exception) {
                AppLog.write("eyes", "face landmarker could not start on $delegate: $e")
            }
        }
        return null
    }

    /** Runs on MediaPipe's thread. */
    private fun onResult(result: FaceLandmarkerResult, width: Int, height: Int) {
        val timeMs = result.timestampMs()
        val points = result.faceLandmarks().firstOrNull()
        val shapes = result.faceBlendshapes().orElse(null)?.firstOrNull()
        val sample = if (points == null || shapes == null) {
            EyeSample(timeMs, faceFound = false)
        } else {
            val scores = shapes.associate { it.categoryName() to it.score() }
            val score = { name: String -> scores[name] ?: 0f }
            EyeSample(
                timeMs = timeMs,
                faceFound = true,
                leftOpen = 1 - score("eyeBlinkLeft"),
                rightOpen = 1 - score("eyeBlinkRight"),
                yawDeg = FaceMesh.headTurnDeg(points[FaceMesh.NOSE_TIP].x(), points[FaceMesh.FACE_SIDE_A].x(), points[FaceMesh.FACE_SIDE_B].x()),
                gaze = FaceMesh.gaze(score),
                dots = if (dotsOn) dots(points, width, height) else null,
            )
        }
        mainExecutor.execute { publish(sample, width, height) }
    }

    private fun publish(sample: EyeSample, width: Int, height: Int) {
        _samples.value = sample
        onSample(sample)
        _fps.value = fpsMeter.onFrame(sample.timeMs)
        summary.add(sample)?.let { AppLog.write("eyes", it) }
        val now = SystemClock.elapsedRealtime()
        detectTotalMs += now - sample.timeMs
        detectFrames++
        if (now - lastDetectLogMs >= DETECT_LOG_EVERY_MS) {
            AppLog.write("eyes", "frame to result ${detectTotalMs / detectFrames} ms avg, image ${width}x$height")
            lastDetectLogMs = now
            detectTotalMs = 0
            detectFrames = 0
        }
    }

    private fun dots(points: List<NormalizedLandmark>, width: Int, height: Int): FaceDots {
        fun at(indices: List<Int>) = indices.map { Dot(points[it].x(), points[it].y()) }
        fun inPixels(indices: List<Int>) = indices.map { Dot(points[it].x() * width, points[it].y() * height) }
        return FaceDots(
            leftEye = at(FaceMesh.LEFT_EYE),
            rightEye = at(FaceMesh.RIGHT_EYE),
            imageAspect = width.toFloat() / height,
            leftShape = eyeShape(inPixels(FaceMesh.LEFT_EYE)),
            rightShape = eyeShape(inPixels(FaceMesh.RIGHT_EYE)),
            irisCentres = at(FaceMesh.IRIS_CENTRES),
            irisRims = at(FaceMesh.IRIS_RIMS),
        )
    }

    private companion object {
        const val MODEL = "face_landmarker.task"
        const val DETECT_LOG_EVERY_MS = 5_000L
    }
}
