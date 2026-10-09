package com.outspoken.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.outspoken.blink.BlinkSettings
import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import com.outspoken.listen.QuickTopics
import com.outspoken.scan.GazeSettings
import com.outspoken.setup.ModelChoice
import com.outspoken.setup.Tuning
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong

data class SetupStatus(
    val modelLine: String,
    val offlineVoice: Boolean?,
    val lastReplyLine: String = "none yet",
    val listenLine: String = "off",
)

/** One model in the Models list: [status] in plain words, as shown. */
data class ModelRow(
    val choice: ModelChoice,
    val status: String,
    val downloaded: Boolean,
    val inUse: Boolean,
)

private const val GRAPH_MS = 5_000L
private const val GAZE_SCALE = 1.6f

/** The gaze box spans this many "line units" each way, so both look lines sit inside it. */
private const val BOX_LINE_UNITS = 1.5f
private val LeftColor = Color(0xFF1E88E5)
private val RightColor = Color(0xFFF4511E)
private val OpenColor = Color(0xFF43A047)
private val ShutColor = Color(0xFFE53935)
private val BetweenColor = Color(0xFFFDD835)

/**
 * Debug screen: the camera with the eye outlines and irises the face tracker found, a graph of
 * both eye-open values against the blink lines, where the eyes look, the latest blink decisions,
 * and sliders to tune blinks and scan speed on the phone.
 */
@Composable
fun EyeCheckScreen(
    cameraGranted: Boolean,
    sample: EyeSample?,
    fps: Float,
    tuning: Tuning,
    recentLines: List<String>,
    setup: SetupStatus,
    onTuningChange: (Tuning) -> Unit,
    onTuningReset: () -> Unit,
    onRequestCamera: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onPreviewGone: (PreviewView) -> Unit,
    restGaze: Float?,
    restIris: Float?,
    steadyGaze: Dot?,
    steadyIris: Float?,
    onChooseModel: () -> Unit,
    onSaveLogs: () -> Unit,
    models: List<ModelRow> = emptyList(),
    canSeeDownloads: Boolean = true,
    onAllowDownloads: () -> Unit = {},
    onDownloadModel: (ModelChoice) -> Unit = {},
    onUseModel: (ModelChoice) -> Unit = {},
    onCalibrate: () -> Unit,
    onAsk: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    val settings = tuning.blink
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
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("Back to talking") }
                Spacer(Modifier.width(12.dp))
                EyeState(sample, settings, Modifier.weight(1f))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            ) {
                if (cameraGranted) {
                    EyeMonitor(sample, settings, onPreviewReady, onPreviewGone, Modifier.fillMaxSize())
                } else {
                    Button(onClick = onRequestCamera) { Text("Allow camera") }
                }
            }
            // Sized to be read from about two metres while someone blinks for an audience.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1.4f)
                    .verticalScroll(rememberScrollState())
            ) {
                EyeGraph(
                    history,
                    settings,
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Decisions(recentLines) }
                    Spacer(Modifier.width(8.dp))
                    GazeBox(steadyGaze ?: sample?.gaze, restGaze, steadyIris ?: sample?.irisY, restIris, tuning.activeGaze, Modifier.size(140.dp))
                }
                EyeNumbers(sample, fps, settings)
                recentLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                AskBox(setup.listenLine, onAsk)
                Button(onClick = onCalibrate, modifier = Modifier.padding(top = 12.dp)) { Text("Calibrate my eyes (30 s)") }
                TuningSliders(tuning, onTuningChange, onTuningReset)
                Text("Model: ${setup.modelLine}", style = MaterialTheme.typography.bodySmall)
                ModelList(models, canSeeDownloads, onAllowDownloads, onDownloadModel, onUseModel)
                Button(onClick = onChooseModel) { Text("Choose model file") }
                Button(onClick = onSaveLogs) { Text("Save logs") }
                Text("Last replies: ${setup.lastReplyLine}", style = MaterialTheme.typography.bodySmall)
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

/** Open, shut or in between, in large coloured letters, from the lower of the two eye values. */
@Composable
private fun EyeState(sample: EyeSample?, settings: BlinkSettings, modifier: Modifier) {
    val open = sample?.takeIf { it.faceFound }?.let { s -> listOfNotNull(s.leftOpen, s.rightOpen).minOrNull() }
    val (word, color) = when {
        open == null -> "NO FACE" to Color.Gray
        open < settings.closedBelow -> "SHUT" to ShutColor
        open > settings.openAbove -> "OPEN" to OpenColor
        else -> "BETWEEN" to BetweenColor
    }
    Text(word, color = color, style = MaterialTheme.typography.displaySmall, modifier = modifier)
}

/** The last few blink, look and choose decisions, large enough to read across a table. */
@Composable
private fun Decisions(lines: List<String>) {
    val decisions = lines.filter { line -> NOT_DECISIONS.none { it in line } }.takeLast(4)
    if (decisions.isEmpty()) Text("No blinks yet", style = MaterialTheme.typography.titleMedium)
    decisions.forEach { Text(it, style = MaterialTheme.typography.titleMedium) }
}

/** Running commentary rather than a decision; still in the small list further down. */
private val NOT_DECISIONS = listOf("eyes shut (", "eyes open after", "face found", "face lost", "speech done")

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

/** Both eye-open values over the last few seconds, with the shut and open lines dashed. */
@Composable
private fun EyeGraph(history: List<EyeSample>, settings: BlinkSettings, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        fun y(value: Float) = size.height * (1 - value)
        val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
        val line = 3.dp.toPx()
        drawLine(ShutColor, Offset(0f, y(settings.closedBelow)), Offset(size.width, y(settings.closedBelow)), strokeWidth = line, pathEffect = dash)
        drawLine(OpenColor, Offset(0f, y(settings.openAbove)), Offset(size.width, y(settings.openAbove)), strokeWidth = line, pathEffect = dash)
        drawLine(outline, Offset(0f, size.height), Offset(size.width, size.height))
        val end = history.lastOrNull()?.timeMs ?: return@Canvas
        fun x(timeMs: Long) = size.width * (1 - (end - timeMs).toFloat() / GRAPH_MS)
        fun trace(color: Color, value: (EyeSample) -> Float?) {
            var last: Offset? = null
            history.forEach { s ->
                val v = value(s)
                val point = if (s.faceFound && v != null) Offset(x(s.timeMs), y(v)) else null
                val from = last
                if (from != null && point != null) drawLine(color, from, point, strokeWidth = 4.dp.toPx())
                last = point
            }
        }
        trace(LeftColor) { it.leftOpen }
        trace(RightColor) { it.rightOpen }
    }
}

/**
 * Where the eyes look, measured the way the highlight uses it: up and down from the resting gaze,
 * so still eyes sit in the middle. The dashed lines are the look up and look down lines; crossing
 * one moves the highlight. (Drawing the raw gaze pinned the dot to the bottom, since with the
 * phone below eye level the resting gaze already reads about 0.5 down.)
 */
@Composable
private fun GazeBox(gaze: Dot?, restGaze: Float?, irisY: Float?, restIris: Float?, settings: GazeSettings, modifier: Modifier) {
    // Both directions in "line units": 1 is exactly on the line that moves the highlight.
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val outline = MaterialTheme.colorScheme.outline
        val dot = MaterialTheme.colorScheme.primary
        Canvas(modifier.border(1.dp, outline)) {
            fun yOf(units: Float) = size.height * (1 + (units / BOX_LINE_UNITS).coerceIn(-1f, 1f)) / 2
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawLine(outline, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
            drawLine(outline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            drawLine(OpenColor, Offset(0f, yOf(-1f)), Offset(size.width, yOf(-1f)), pathEffect = dash)
            if (irisLine != null || downLine != null) drawLine(OpenColor, Offset(0f, yOf(1f)), Offset(size.width, yOf(1f)), pathEffect = dash)
            if (gaze == null) return@Canvas
            val x = (gaze.x * GAZE_SCALE).coerceIn(-1f, 1f)
            drawCircle(dot, radius = 10.dp.toPx(), center = Offset(size.width * (1 + x) / 2, yOf(value)))
        }
        Text(gazeWords(value, irisLine != null), style = MaterialTheme.typography.titleMedium)
    }
}

private fun gazeWords(value: Float, iris: Boolean): String = when {
    value <= -1f -> "up: move up"
    value >= 1f -> "down: move down"
    else -> "at rest" + if (iris) " (down by iris)" else ""
}

@Composable
fun EyeNumbers(sample: EyeSample?, fps: Float, settings: BlinkSettings) {
    if (sample == null || !sample.faceFound) {
        Text("Looking for you")
        Text("Camera: ${fps.toInt()} fps")
        return
    }
    val dots = sample.dots
    Text("Left eye (blue): ${sample.leftOpen.formatOpen()}" + (dots?.leftShape?.let { ", shape ${it.formatOpen()}" } ?: ""))
    Text("Right eye (orange): ${sample.rightOpen.formatOpen()}" + (dots?.rightShape?.let { ", shape ${it.formatOpen()}" } ?: ""))
    val turnOk = abs(sample.yawDeg) <= settings.maxHeadTurnDeg
    Text("Head turn: ${sample.yawDeg.toInt()}° ${if (turnOk) "ok" else "too far"}")
    Text("Camera: ${fps.toInt()} fps")
}

@Composable
private fun TuningSliders(tuning: Tuning, onChange: (Tuning) -> Unit, onReset: () -> Unit) {
    val blink = tuning.blink
    Text("Tuning (saved on the phone)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    LabeledSlider("Shut line (red): ${blink.closedBelow.formatOpen()}", blink.closedBelow, 0.05f..0.9f) {
        onChange(tuning.copy(blink = blink.copy(closedBelow = it, openAbove = maxOf(blink.openAbove, it + 0.05f))))
    }
    LabeledSlider("Open line (green): ${blink.openAbove.formatOpen()}", blink.openAbove, 0.1f..0.95f) {
        onChange(tuning.copy(blink = blink.copy(openAbove = it, closedBelow = minOf(blink.closedBelow, it - 0.05f))))
    }
    LabeledSlider("Shortest blink: ${blink.minBlinkMs} ms", blink.minBlinkMs.toFloat(), 100f..600f) {
        onChange(tuning.copy(blink = blink.copy(minBlinkMs = it.roundTo(10))))
    }
    LabeledSlider("Longest blink: ${blink.maxBlinkMs} ms", blink.maxBlinkMs.toFloat(), 600f..2_000f) {
        onChange(tuning.copy(blink = blink.copy(maxBlinkMs = it.roundTo(50))))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (tuning.moveByEyes) "Mode: eyes. Look up for the next card, blink to choose" else "Mode: blink only. The highlight moves on a timer, blink to choose",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = tuning.moveByEyes, onCheckedChange = { onChange(tuning.copy(moveByEyes = it)) })
    }
    if (tuning.moveByEyes) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Looking down also moves (off: look up goes to the next card)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = tuning.lookDown, onCheckedChange = { onChange(tuning.copy(lookDown = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (tuning.winks) "Winks move too (left wink down, right wink up)" else "Winks do nothing",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = tuning.winks, onCheckedChange = { onChange(tuning.copy(winks = it)) })
        }
        LabeledSlider("Look up distance: ${tuning.gaze.lookStrength.formatOpen()}", tuning.gaze.lookStrength, 0.1f..0.9f) {
            onChange(tuning.copy(gaze = tuning.gaze.copy(lookStrength = it)))
        }
        tuning.gaze.irisDownStrength?.takeIf { tuning.lookDown }?.let { iris ->
            LabeledSlider("Look down (iris): ${String.format(Locale.US, "%.3f", iris)} - lower is more sensitive", iris, 0.005f..0.08f) {
                onChange(tuning.copy(gaze = tuning.gaze.copy(irisDownStrength = it)))
            }
        }
        if (tuning.lookDown) {
            val down = tuning.gaze.downStrength
            LabeledSlider("Look down distance: ${down?.formatOpen() ?: "off"}", down ?: 0f, 0f..0.9f) {
                onChange(tuning.copy(gaze = tuning.gaze.copy(downStrength = it.takeIf { v -> v >= 0.05f })))
            }
        }
        LabeledSlider("Look hold: ${tuning.gaze.lookHoldMs} ms", tuning.gaze.lookHoldMs.toFloat(), 100f..1_000f) {
            onChange(tuning.copy(gaze = tuning.gaze.copy(lookHoldMs = it.roundTo(10))))
        }
    } else {
        LabeledSlider("Scan speed: ${tuning.scanMs} ms per card", tuning.scanMs.toFloat(), 600f..3_000f) {
            onChange(tuning.copy(scanMs = it.roundTo(100)))
        }
    }
    OutlinedButton(onClick = onReset) { Text("Reset to defaults") }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
}

private fun Float.roundTo(step: Int): Long = (this / step).roundToLong() * step

private fun Float?.formatOpen() = this?.let { "%.2f".format(Locale.US, it) } ?: "-"

/** The visitor's question when the microphone cannot hear it: typed, or one tap on a topic (PRD F7). */
@Composable
private fun AskBox(listenLine: String, onAsk: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    Text("Listening: $listenLine", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text("Type the visitor's question") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                onAsk(typed.trim())
                typed = ""
            },
            enabled = typed.isNotBlank(),
        ) { Text("Ask") }
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        QuickTopics.all.forEach { (label, question) ->
            AssistChip(onClick = { onAsk(question) }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

/**
 * The models the app offers. Download opens the file in the phone's browser (the app itself has no
 * internet); the app picks the finished file up from Downloads and loads it.
 */
@Composable
private fun ModelList(
    models: List<ModelRow>,
    canSeeDownloads: Boolean,
    onAllowDownloads: () -> Unit,
    onDownload: (ModelChoice) -> Unit,
    onUse: (ModelChoice) -> Unit,
) {
    Text("Models", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    if (!canSeeDownloads) {
        Text(
            "To load a downloaded model by itself, the app needs to see the Downloads folder.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onAllowDownloads) { Text("Allow access to Downloads") }
    }
    models.forEach { row ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${row.choice.title}, ${row.choice.sizeGb}: ${row.choice.note}", style = MaterialTheme.typography.bodyMedium)
                Text(row.status, style = MaterialTheme.typography.bodySmall)
            }
            when {
                row.inUse -> Unit
                row.downloaded -> Button(onClick = { onUse(row.choice) }) { Text("Use") }
                else -> Button(onClick = { onDownload(row.choice) }) { Text("Download") }
            }
        }
    }
}
