package com.outspoken.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.outspoken.blink.BlinkSettings
import com.outspoken.eye.EyeSample

/**
 * Calibration: the spoken prompt in large text, how far along it is, and the camera so the person
 * can see their eyes are found. No design yet, so plain Material 3.
 */
@Composable
fun CalibrationScreen(
    prompt: String,
    progress: Float,
    outcome: String?,
    failed: Boolean,
    sample: EyeSample?,
    settings: BlinkSettings,
    onPreviewReady: (PreviewView) -> Unit,
    onPreviewGone: (PreviewView) -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Calibration", style = MaterialTheme.typography.titleMedium)
            Text(outcome ?: prompt, style = MaterialTheme.typography.headlineLarge)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            EyeMonitor(
                sample,
                settings,
                onPreviewReady,
                onPreviewGone,
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (failed) Button(onClick = onRetry) { Text("Try again") }
                OutlinedButton(onClick = onSkip) { Text(if (outcome == null) "Skip" else "Back") }
            }
        }
    }
}
