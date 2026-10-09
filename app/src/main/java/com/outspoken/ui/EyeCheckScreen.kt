package com.outspoken.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.outspoken.eye.EyeSample
import java.util.Locale

data class SetupStatus(
    val modelLine: String,
    val offlineVoice: Boolean?,
    val lastReplyLine: String,
    val details: List<String> = emptyList(),
)

/** M0 check screen: live eye-open numbers plus the setup items that must be on the phone. */
@Composable
fun EyeCheckScreen(
    cameraGranted: Boolean,
    sample: EyeSample?,
    fps: Float,
    setup: SetupStatus,
    onRequestCamera: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onPreviewGone: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (cameraGranted) {
                    CameraPreview(onPreviewReady, onPreviewGone, Modifier.fillMaxSize())
                } else {
                    Button(onClick = onRequestCamera) { Text("Allow camera") }
                }
            }
            EyeNumbers(sample, fps)
            Text("Model: ${setup.modelLine}")
            Text("Last replies: ${setup.lastReplyLine}")
            setup.details.forEach { Text(it) }
            Text(
                "Offline voice: " + when (setup.offlineVoice) {
                    null -> "checking"
                    true -> "ready"
                    false -> "missing"
                }
            )
        }
    }
}

@Composable
fun CameraPreview(onReady: (PreviewView) -> Unit, onGone: () -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> PreviewView(context).also(onReady) },
        modifier = modifier,
        onRelease = { onGone() },
    )
}

@Composable
fun EyeNumbers(sample: EyeSample?, fps: Float) {
    if (sample == null || !sample.faceFound) {
        Text("Looking for you")
        return
    }
    Text("Left eye: ${sample.leftOpen.formatOpen()}")
    Text("Right eye: ${sample.rightOpen.formatOpen()}")
    Text("Head: yaw ${sample.yawDeg.toInt()}°, pitch ${sample.pitchDeg.toInt()}°")
    Text("Camera: ${fps.toInt()} fps")
}

private fun Float?.formatOpen() = this?.let { "%.2f".format(Locale.US, it) } ?: "-"
