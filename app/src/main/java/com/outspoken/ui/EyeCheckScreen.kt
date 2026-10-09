package com.outspoken.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import java.util.Locale
import kotlin.math.abs

/** The blink detector's current lines and limits, for drawing. */
data class EyeLines(
    val closedBelow: Float,
    val openAbove: Float,
    val minBlinkMs: Long,
    val maxBlinkMs: Long,
    val maxHeadTurnDeg: Float,
    val maxHeadTiltDeg: Float,
)

data class SetupStatus(
    val modelLine: String,
    val offlineVoice: Boolean?,
    val lastReplyLine: String,
    val details: List<String> = emptyList(),
)

private const val GRAPH_MS = 5_000L
private val LeftColor = Color(0xFF1E88E5)
private val RightColor = Color(0xFFF4511E)
private val OpenColor = Color(0xFF43A047)
private val ShutColor = Color(0xFFE53935)
private val BetweenColor = Color(0xFFFDD835)

/**
 * Debug screen: the camera with the eye outlines ML Kit found drawn on top, a graph of both
 * eye-open values against the blink lines, the latest blink decisions, and the setup checks.
 */
@Composable
fun EyeCheckScreen(
    cameraGranted: Boolean,
    sample: EyeSample?,
    fps: Float,
    settings: EyeLines,
    recentLines: List<String>,
    setup: SetupStatus,
    onRequestCamera: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onPreviewGone: () -> Unit,
    onChooseModel: () -> Unit,
) {
    val history = remember { mutableStateListOf<EyeSample>() }
    LaunchedEffect(sample) {
        if (sample == null) return@LaunchedEffect
        history += sample
        while (history.isNotEmpty() && sample.timeMs - history.first().timeMs > GRAPH_MS) history.removeAt(0)
    }

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
                    EyeDots(sample, settings, Modifier.fillMaxSize())
                } else {
                    Button(onClick = onRequestCamera) { Text("Allow camera") }
                }
            }
            EyeGraph(
                history,
                settings,
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(vertical = 8.dp)
            )
            EyeNumbers(sample, fps, settings)
            recentLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Model: ${setup.modelLine}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onChooseModel) { Text("Choose model file") }
            Text("Last replies: ${setup.lastReplyLine}", style = MaterialTheme.typography.bodySmall)
            setup.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Offline voice: " + when (setup.offlineVoice) {
                    null -> "checking"
                    true -> "ready"
                    false -> "missing"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun CameraPreview(onReady: (PreviewView) -> Unit, onGone: () -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // Fit, not fill, so the whole camera frame shows and the dots can be placed on it.
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }.also(onReady)
        },
        modifier = modifier,
        onRelease = { onGone() },
    )
}

/** Dots on each eye outline, coloured by what the blink detector reads from that eye. */
@Composable
private fun EyeDots(sample: EyeSample?, settings: EyeLines, modifier: Modifier) {
    Canvas(modifier) {
        if (sample == null) return@Canvas
        val dots = sample.dots ?: return@Canvas
        val frame = fitted(dots.imageAspect)
        drawOutline(dots.leftEye, frame, eyeColor(sample.leftOpen, settings))
        drawOutline(dots.rightEye, frame, eyeColor(sample.rightOpen, settings))
    }
}

/** Where the camera frame sits inside this canvas when shown with FIT_CENTER. */
private fun DrawScope.fitted(aspect: Float): Rect {
    val width = minOf(size.width, size.height * aspect)
    val height = width / aspect
    val left = (size.width - width) / 2
    val top = (size.height - height) / 2
    return Rect(left, top, left + width, top + height)
}

private fun DrawScope.drawOutline(points: List<Dot>, frame: Rect, color: Color) {
    // The front camera preview is mirrored; the analysed image is not.
    points.forEach { drawCircle(color, radius = 3.dp.toPx(), center = Offset(frame.left + (1 - it.x) * frame.width, frame.top + it.y * frame.height)) }
}

private fun eyeColor(open: Float?, settings: EyeLines) = when {
    open == null -> Color.Gray
    open < settings.closedBelow -> ShutColor
    open > settings.openAbove -> OpenColor
    else -> BetweenColor
}

/** Both eye-open values over the last few seconds, with the shut and open lines dashed. */
@Composable
private fun EyeGraph(history: List<EyeSample>, settings: EyeLines, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        fun y(value: Float) = size.height * (1 - value)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        drawLine(ShutColor, Offset(0f, y(settings.closedBelow)), Offset(size.width, y(settings.closedBelow)), pathEffect = dash)
        drawLine(OpenColor, Offset(0f, y(settings.openAbove)), Offset(size.width, y(settings.openAbove)), pathEffect = dash)
        drawLine(outline, Offset(0f, size.height), Offset(size.width, size.height))
        val end = history.lastOrNull()?.timeMs ?: return@Canvas
        fun x(timeMs: Long) = size.width * (1 - (end - timeMs).toFloat() / GRAPH_MS)
        fun trace(color: Color, value: (EyeSample) -> Float?) {
            var last: Offset? = null
            history.forEach { s ->
                val v = value(s)
                val point = if (s.faceFound && v != null) Offset(x(s.timeMs), y(v)) else null
                val from = last
                if (from != null && point != null) drawLine(color, from, point, strokeWidth = 2.dp.toPx())
                last = point
            }
        }
        trace(LeftColor) { it.leftOpen }
        trace(RightColor) { it.rightOpen }
    }
}

@Composable
fun EyeNumbers(sample: EyeSample?, fps: Float, settings: EyeLines) {
    if (sample == null || !sample.faceFound) {
        Text("Looking for you")
        Text("Camera: ${fps.toInt()} fps")
        return
    }
    val dots = sample.dots
    Text("Left eye (blue): ${sample.leftOpen.formatOpen()}" + (dots?.leftShape?.let { ", shape ${it.formatOpen()}" } ?: ""))
    Text("Right eye (orange): ${sample.rightOpen.formatOpen()}" + (dots?.rightShape?.let { ", shape ${it.formatOpen()}" } ?: ""))
    val turnOk = abs(sample.yawDeg) <= settings.maxHeadTurnDeg
    val tiltOk = abs(sample.pitchDeg) <= settings.maxHeadTiltDeg
    Text(
        "Head: turn ${sample.yawDeg.toInt()}° ${if (turnOk) "ok" else "too far"}, " +
            "tilt ${sample.pitchDeg.toInt()}° ${if (tiltOk) "ok" else "too far"}"
    )
    Text("Camera: ${fps.toInt()} fps. Blink = both eyes below ${settings.closedBelow.formatOpen()} for ${settings.minBlinkMs}-${settings.maxBlinkMs} ms")
}

private fun Float?.formatOpen() = this?.let { "%.2f".format(Locale.US, it) } ?: "-"
