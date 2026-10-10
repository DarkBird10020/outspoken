package com.outspoken.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.blink.BlinkSettings
import com.outspoken.eye.EyeSample
import com.outspoken.setup.Calibration
import com.outspoken.ui.theme.BoxShadow
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.rgba
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/**
 * Calibration (designed): the spoken prompt as the heading, the camera so the person can see
 * their eyes are found, which of the five steps is being measured, the eye-open value and the time
 * left. A failed calibration shows the reason and Try again (not in the design).
 */
@Composable
fun CalibrationScreen(
    prompt: String,
    stage: Int,
    secondsLeft: Int,
    outcome: String?,
    failed: Boolean,
    sample: EyeSample?,
    settings: BlinkSettings,
    onPreviewReady: (PreviewView) -> Unit,
    onPreviewGone: (PreviewView) -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    DesignScreen(Modifier.dottedCanvas(), gap = 16.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(R.drawable.ic_back, "Back to talking", onSkip)
            Pill(Surfaces.Glass) { Text("Step ${stage.coerceIn(1, Calibration.STAGES)} of ${Calibration.STAGES}", style = type(15, 500)) }
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(outcome ?: prompt, style = type(34, 400, lineHeight = 1.08f))
            Text(
                when {
                    failed -> "Try again, or skip for now and use the last settings."
                    outcome != null -> "The app is set to your eyes."
                    else -> "Hold it until the next step is spoken. This sets the app to your eyes."
                },
                style = type(16, color = InkSoft, lineHeight = 1.35f),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .surface(Surfaces.Camera, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp)),
        ) {
            EyeMonitor(sample, settings, onPreviewReady, onPreviewGone, Modifier.fillMaxSize())
            Text(
                "Live camera",
                style = type(13, color = rgba(255, 255, 255, 0.7f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .surface(Surfaces.Green, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stageTitle(stage), style = type(15, 500))
                Text(if (sample?.faceFound == true) "Steady" else "Looking for you", style = type(15, 500))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(Calibration.STAGES) { index ->
                    val done = index < stage
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .surface(if (done) SegmentDone else SegmentTodo, RoundedCornerShape(4.dp))
                    )
                }
            }
            nextLine(stage)?.let { Text(it, style = type(13)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val open = sample?.takeIf { it.faceFound }?.let { s -> listOfNotNull(s.leftOpen, s.rightOpen).takeIf { it.isNotEmpty() }?.average()?.toFloat() }
            Tile("Eyes open", open.formatOpen(), Modifier.weight(1f))
            Tile("Time left", "$secondsLeft s", Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        if (failed) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Skip for now", onSkip, Modifier.weight(1f), height = 60, size = 18, weight = 600)
                PillButton("Try again", onRetry, Modifier.weight(1f), style = Surfaces.StartButton, height = 60, size = 18, weight = 600)
            }
        } else {
            PillButton(if (outcome == null) "Skip for now" else "Back to talking", onSkip, Modifier.fillMaxWidth(), height = 60, size = 18, weight = 600)
        }
    }
}

private fun stageTitle(stage: Int) = when (stage) {
    1 -> "Measuring your eyes at rest"
    2 -> "Measuring your look up"
    3 -> "Measuring your look down"
    4 -> "Measuring your closed eyes"
    else -> "Done"
}

private fun nextLine(stage: Int) = when (stage) {
    1 -> "Next: look up."
    2 -> "Next: look down."
    3 -> "Next: close your eyes."
    4 -> "Next: done."
    else -> null
}


private val SegmentDone = SurfaceStyle(
    base = Color.White,
    shadows = listOf(BoxShadow(y = 2.dp, blur = 8.dp, color = rgba(20, 90, 40, 0.35f))),
)

private val SegmentTodo = SurfaceStyle(base = rgba(255, 255, 255, 0.45f))
