package com.outspoken.eye

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EyeReader : ImageAnalysis.Analyzer {

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

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        // Sensor timestamp, so blink durations are not skewed by detector latency.
        val timeMs = image.imageInfo.timestamp / 1_000_000
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                _samples.value = face?.toSample(timeMs) ?: EyeSample(timeMs, faceFound = false)
                _fps.value = fpsMeter.onFrame(timeMs)
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
