package com.outspoken.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.outspoken.R
import com.outspoken.blink.BlinkSettings
import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import com.outspoken.scan.GazeSettings
import com.outspoken.setup.ModelChoice
import com.outspoken.setup.Tuning
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.rgba
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type
import java.util.Locale
import kotlin.math.hypot

/** One model in the Models list: [status] in plain words, as shown. */
data class ModelRow(
    val choice: ModelChoice,
    val status: String,
    val downloaded: Boolean,
    val inUse: Boolean,
)

private const val GRAPH_MS = 5_000L

/** The gaze tile's dot moves this many "line units" each way at most, so the look up line stays inside. */
private const val BOX_LINE_UNITS = 1.5f
private const val GAZE_SCALE = 1.6f

private val LeftEyeColor = Color(0xFF9F9BE0)
private val RightEyeColor = Ink
private val OpenLineColor = Color(0xFF2E9B48)
private val ShutLineColor = Color(0xFFF04E88)

// Colours of the eye dots drawn on the camera, by blink reading.
private val OpenColor = Color(0xFF43A047)
private val ShutColor = Color(0xFFE53935)
private val BetweenColor = Color(0xFFFDD835)


/**
 * Eye check (designed): the live camera with the eye outlines, a graph of both eye-open values
 * against the open and shut lines, where the eyes look, the camera speed, both eye values and the
 * last blink decision. Settings and the model are one tap further.
 */
@Composable
fun EyeCheckScreen(
    cameraGranted: Boolean,
    sample: EyeSample?,
    fps: Float,
    tuning: Tuning,
    recentLines: List<String>,
    onRequestCamera: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onPreviewGone: (PreviewView) -> Unit,
    restGaze: Float?,
    restIris: Float?,
    steadyGaze: Dot?,
    steadyIris: Float?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    val settings = tuning.blink
    val history = remember { mutableStateListOf<EyeSample>() }
    LaunchedEffect(sample) {
        if (sample == null) return@LaunchedEffect
        history += sample
        while (history.isNotEmpty() && sample.timeMs - history.first().timeMs > GRAPH_MS) history.removeAt(0)
    }

    DesignScreen(Modifier.dottedCanvas().verticalScroll(rememberScrollState()), gap = 14.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(R.drawable.ic_back, "Back to talking", onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EyeStatePill(sample, settings)
                CircleIconButton(R.drawable.ic_settings, "Settings", onSettings)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .surface(Surfaces.Camera, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (cameraGranted) {
                EyeMonitor(sample, settings, onPreviewReady, onPreviewGone, Modifier.fillMaxSize())
                Text(
                    "Live camera",
                    style = type(13, color = rgba(255, 255, 255, 0.7f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                )
            } else {
                PillButton(
                    "Allow camera",
                    onRequestCamera,
                    Modifier
                        .padding(horizontal = 40.dp)
                        .fillMaxWidth(),
                    style = Surfaces.StartButton,
                    weight = 600,
                )
            }
        }
        GraphCard(history, settings)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GazeTile(steadyGaze ?: sample?.gaze, restGaze, steadyIris ?: sample?.irisY, restIris, tuning.activeGaze, Modifier.weight(1f))
            Tile("Camera", "${fps.toInt()} fps", Modifier.weight(1f).height(92.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Left eye", sample?.takeIf { it.faceFound }?.leftOpen.formatOpen(), Modifier.weight(1f))
            Tile("Right eye", sample?.takeIf { it.faceFound }?.rightOpen.formatOpen(), Modifier.weight(1f))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .surface(Surfaces.Pink, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Last blink", style = type(13))
            Text(lastBlink(recentLines), style = type(20, 500, lineHeight = 1.2f))
        }
    }
}

/** "Eyes open" on green, "Eyes shut" on pink, otherwise glass; read from the lower of the two eyes. */
@Composable
private fun EyeStatePill(sample: EyeSample?, settings: BlinkSettings) {
    val open = sample?.takeIf { it.faceFound }?.let { listOfNotNull(it.leftOpen, it.rightOpen).minOrNull() }
    when {
        open == null -> DotPill("Looking for you", InkFaint)
        open < settings.closedBelow -> DotPill("Eyes shut", Ink, Surfaces.Pink)
        open > settings.openAbove -> DotPill("Eyes open", Ink, Surfaces.OfflinePill)
        else -> DotPill("Half open", InkSoft)
    }
}

/** The newest blink or pick decision in plain words, from the on-screen log lines. */
internal fun lastBlink(lines: List<String>): String {
    val line = lines.lastOrNull { line ->
        (line.startsWith("blink: ") && NOT_DECISIONS.none { it in line }) ||
            line.startsWith("scan: blink picked") || line.startsWith("scan: say ")
    } ?: return "No blinks yet"
    return line.substringAfter(": ").replaceFirstChar { it.uppercase() }
}

/** Running commentary rather than a decision. */
private val NOT_DECISIONS = listOf("eyes shut (", "eyes open after", "face found", "face lost")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphCard(history: List<EyeSample>, settings: BlinkSettings) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("How open your eyes are", style = type(15, 500))
            Text("last 5 s", style = type(13, color = InkSoft))
        }
        EyeGraph(
            history,
            settings,
            Modifier
                .fillMaxWidth()
                .height(130.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Legend("Left eye", LeftEyeColor)
            Legend("Right eye", RightEyeColor)
            Legend("Open line", OpenLineColor)
            Legend("Shut line", ShutLineColor)
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .surface(SurfaceStyle(base = color), CircleShape)
        )
        Text(text, style = type(13, color = InkSoft))
    }
}

/** Both eye-open values over the last few seconds, with the open and shut lines dashed. */
@Composable
private fun EyeGraph(history: List<EyeSample>, settings: BlinkSettings, modifier: Modifier) {
    Canvas(modifier) {
        fun y(value: Float) = size.height * (1 - value)
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        val line = 2.dp.toPx()
        drawLine(OpenLineColor, Offset(0f, y(settings.openAbove)), Offset(size.width, y(settings.openAbove)), strokeWidth = line, pathEffect = dash)
        drawLine(ShutLineColor, Offset(0f, y(settings.closedBelow)), Offset(size.width, y(settings.closedBelow)), strokeWidth = line, pathEffect = dash)
        val end = history.lastOrNull()?.timeMs ?: return@Canvas
        fun x(timeMs: Long) = size.width * (1 - (end - timeMs).toFloat() / GRAPH_MS)
        fun trace(color: Color, value: (EyeSample) -> Float?) {
            var last: Offset? = null
            history.forEach { s ->
                val v = value(s)
                val point = if (s.faceFound && v != null) Offset(x(s.timeMs), y(v)) else null
                val from = last
                if (from != null && point != null) drawLine(color, from, point, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                last = point
            }
        }
        trace(LeftEyeColor) { it.leftOpen }
        trace(RightEyeColor) { it.rightOpen }
    }
}

/**
 * Where the eyes look, measured the way the highlight uses it: up and down from the resting gaze,
 * so still eyes sit in the middle. The dashed line is the look up line (and the look down line
 * when looking down is on); crossing one moves the highlight.
 */
@Composable
private fun GazeTile(gaze: Dot?, restGaze: Float?, irisY: Float?, restIris: Float?, settings: GazeSettings, modifier: Modifier) {
    val dy = if (gaze != null && restGaze != null) gaze.y - restGaze else null
    val upProgress = dy?.let { -it / settings.lookStrength } ?: 0f
    val irisLine = settings.irisDownStrength
    val downLine = settings.downStrength
    val downProgress = when {
        irisLine != null && irisY != null && restIris != null -> (irisY - restIris) / irisLine
        irisLine == null && downLine != null && dy != null -> dy / downLine
        else -> 0f
    }
    val value = if (upProgress > downProgress) -upProgress else downProgress
    val lookDownOn = irisLine != null || downLine != null
    Row(
        modifier
            .height(92.dp)
            .surface(Surfaces.Glass, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier
                .size(64.dp)
                .surface(GazeBoxSurface, RoundedCornerShape(16.dp))
        ) {
            fun yOf(units: Float) = size.height / 2 + (units / BOX_LINE_UNITS).coerceIn(-1f, 1f) * (size.height / 2 - 7.dp.toPx())
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            drawLine(OpenLineColor, Offset(0f, yOf(-1f)), Offset(size.width, yOf(-1f)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            if (lookDownOn) drawLine(OpenLineColor, Offset(0f, yOf(1f)), Offset(size.width, yOf(1f)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            if (gaze == null) return@Canvas
            val x = size.width / 2 + (gaze.x * GAZE_SCALE).coerceIn(-1f, 1f) * (size.width / 2 - 7.dp.toPx())
            drawCircle(LeftEyeColor.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = Offset(x, yOf(value)))
            drawCircle(LeftEyeColor, radius = 7.dp.toPx(), center = Offset(x, yOf(value)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Gaze", style = type(13, color = InkSoft))
            Text(gazeWords(value), style = type(20, 500))
        }
    }
}

private val GazeBoxSurface = SurfaceStyle(base = rgba(27, 29, 31, 0.06f), border = BorderStroke(1.dp, rgba(27, 29, 31, 0.12f)))

private fun gazeWords(value: Float): String = when {
    value <= -1f -> "Up"
    value >= 1f -> "Down"
    else -> "At rest"
}

/** The front camera with the eyelid and iris dots drawn on it. */
@Composable
fun EyeMonitor(
    sample: EyeSample?,
    settings: BlinkSettings,
    onPreviewReady: (PreviewView) -> Unit,
    onPreviewGone: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        CameraPreview(onPreviewReady, onPreviewGone, Modifier.fillMaxSize())
        EyeDots(sample, settings, Modifier.fillMaxSize())
    }
}

@Composable
fun CameraPreview(onReady: (PreviewView) -> Unit, onGone: (PreviewView) -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // Fit, not fill, so the whole camera frame shows and the dots can be placed on it.
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }.also(onReady)
        },
        modifier = modifier,
        onRelease = { onGone(it) },
    )
}

/** Small dots on each eyelid outline and a ring on each iris, coloured by the blink reading. */
@Composable
private fun EyeDots(sample: EyeSample?, settings: BlinkSettings, modifier: Modifier) {
    Canvas(modifier) {
        if (sample == null) return@Canvas
        val dots = sample.dots ?: return@Canvas
        val frame = fitted(dots.imageAspect)
        // The front camera preview is mirrored; the analysed image is not.
        fun place(dot: Dot) = Offset(frame.left + (1 - dot.x) * frame.width, frame.top + dot.y * frame.height)
        val lid = Color.White.copy(alpha = 0.7f)
        (dots.leftEye + dots.rightEye).forEach { drawCircle(lid, radius = 1.5.dp.toPx(), center = place(it)) }
        val state = eyeColor(minOf(sample.leftOpen ?: 0f, sample.rightOpen ?: 0f), settings)
        dots.irisCentres.forEachIndexed { i, centre ->
            val rim = dots.irisRims.subList(i * 4, i * 4 + 4).map(::place)
            val c = place(centre)
            val radius = rim.map { hypot(it.x - c.x, it.y - c.y) }.average().toFloat()
            drawCircle(state, radius = radius, center = c, style = Stroke(width = 2.dp.toPx()))
            drawCircle(state, radius = 2.dp.toPx(), center = c)
        }
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

private fun eyeColor(open: Float?, settings: BlinkSettings) = when {
    open == null -> Color.Gray
    open < settings.closedBelow -> ShutColor
    open > settings.openAbove -> OpenColor
    else -> BetweenColor
}

internal fun Float?.formatOpen() = this?.let { "%.2f".format(Locale.US, it) } ?: "-"
