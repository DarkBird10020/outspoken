package com.outspoken.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.conversation.AppLanguage
import com.outspoken.hand.HandReading
import com.outspoken.hand.HandSign
import com.outspoken.listen.QuickTopics
import com.outspoken.setup.MIN_PICK_HOLD_MS
import com.outspoken.setup.Tuning
import com.outspoken.ui.theme.BoxShadow
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.rgba
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Settings (designed): the visitor's question typed or picked from a topic, how the highlight
 * moves, and the blink and look tuning saved on the phone. Switches the design does not show
 * (looking down, winks, look up distance) sit in the same card, listed as design gaps.
 */
@Composable
fun SettingsScreen(
    tuning: Tuning,
    listenLine: String,
    handReading: HandReading,
    onTuningChange: (Tuning) -> Unit,
    onTuningReset: () -> Unit,
    onAsk: (String) -> Unit,
    onCalibrate: () -> Unit,
    onModels: () -> Unit,
    onBack: () -> Unit,
) {
    DesignScreen(Modifier.dottedCanvas(), gap = 14.dp, scrolls = true) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(R.drawable.ic_back, "Back to talking", onBack)
            Pill(Surfaces.Glass) { Text("Settings", style = type(15, 500)) }
        }
        AskCard(listenLine, onAsk)
        Segmented("Look up to move", "Blink only", tuning.moveByEyes) { onTuningChange(tuning.copy(moveByEyes = it)) }
        // Not in the design (a design gap): the language of the cards, the voice and listening.
        Segmented(AppLanguage.English.label, AppLanguage.Hindi.label, tuning.language == AppLanguage.English) { english ->
            onTuningChange(tuning.copy(language = if (english) AppLanguage.English else AppLanguage.Hindi))
        }
        TuningCard(tuning, onTuningChange)
        HandCard(tuning, handReading, onTuningChange)
        PillButton("Model and logs", onModels, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Reset", onTuningReset, Modifier.weight(1f))
            PillButton("Calibrate, 30 s", onCalibrate, Modifier.weight(1f), style = Surfaces.StartButton, weight = 600)
        }
    }
}

/** The visitor's question when the microphone cannot hear it: typed, or one tap on a topic (PRD F7). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskCard(listenLine: String, onAsk: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    fun ask() {
        if (typed.isBlank()) return
        onAsk(typed.trim())
        typed = ""
    }
    GlassCard(gap = 12) {
        Text("Ask without the microphone", style = type(15, 500))
        Text("Listening: $listenLine", style = type(13, color = InkSoft))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .surface(FieldSurface, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (typed.isEmpty()) Text("Type the visitor's question", style = type(16, color = InkFaint))
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    textStyle = type(16),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { ask() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Type the visitor's question" },
                )
            }
            PillButton("Ask", ::ask, Modifier.width(72.dp), style = Surfaces.StartButton, height = 52, size = 16, weight = 600)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTopics.all.forEach { (label, question) ->
                PillButton(label, { onAsk(question) }, height = 40, size = 15, sidePadding = 16)
            }
        }
    }
}

@Composable
private fun TuningCard(tuning: Tuning, onChange: (Tuning) -> Unit) {
    val blink = tuning.blink
    GlassCard(gap = 18) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Tuning", style = type(15, 500))
            Text("Saved on this phone", style = type(13, color = InkSoft))
        }
        DesignSlider("Shut line", blink.closedBelow.two(), blink.closedBelow, 0.05f..0.9f) {
            onChange(tuning.copy(blink = blink.copy(closedBelow = it, openAbove = maxOf(blink.openAbove, it + 0.05f))))
        }
        DesignSlider("Open line", blink.openAbove.two(), blink.openAbove, 0.1f..0.95f) {
            onChange(tuning.copy(blink = blink.copy(openAbove = it, closedBelow = minOf(blink.closedBelow, it - 0.05f))))
        }
        DesignSlider("Shortest blink", "${blink.minBlinkMs} ms", blink.minBlinkMs.toFloat(), MIN_PICK_HOLD_MS.toFloat()..800f) {
            onChange(tuning.copy(blink = blink.copy(minBlinkMs = it.roundTo(10))))
        }
        DesignSlider("Longest blink", "${blink.maxBlinkMs} ms", blink.maxBlinkMs.toFloat(), 600f..2_000f) {
            onChange(tuning.copy(blink = blink.copy(maxBlinkMs = it.roundTo(50))))
        }
        if (tuning.moveByEyes) {
            DesignSlider("Look hold", "${tuning.gaze.lookHoldMs} ms", tuning.gaze.lookHoldMs.toFloat(), 100f..1_000f) {
                onChange(tuning.copy(gaze = tuning.gaze.copy(lookHoldMs = it.roundTo(10))))
            }
            DesignSlider("Look up distance", tuning.gaze.lookStrength.two(), tuning.gaze.lookStrength, 0.1f..0.9f) {
                onChange(tuning.copy(gaze = tuning.gaze.copy(lookStrength = it)))
            }
            ToggleRow("Looking down also moves", tuning.lookDown) { onChange(tuning.copy(lookDown = it)) }
            if (tuning.lookDown) {
                tuning.gaze.irisDownStrength?.let { iris ->
                    DesignSlider("Look down (iris)", String.format(Locale.US, "%.3f", iris), iris, 0.005f..0.08f) {
                        onChange(tuning.copy(gaze = tuning.gaze.copy(irisDownStrength = it)))
                    }
                }
                val down = tuning.gaze.downStrength
                DesignSlider("Look down distance", down?.two() ?: "off", down ?: 0f, 0f..0.9f) {
                    onChange(tuning.copy(gaze = tuning.gaze.copy(downStrength = it.takeIf { v -> v >= 0.05f })))
                }
            }
            ToggleRow("Winks move too (left down, right up)", tuning.winks) { onChange(tuning.copy(winks = it)) }
        } else {
            DesignSlider("Scan speed", "${tuning.scanMs} ms a card", tuning.scanMs.toFloat(), 600f..3_000f) {
                onChange(tuning.copy(scanMs = it.roundTo(100)))
            }
        }
    }
}

/**
 * Hand signs (not in the design, listed as a design gap): on or off, what the camera reads now,
 * and each sign on or off for this person.
 */
@Composable
private fun HandCard(tuning: Tuning, reading: HandReading, onChange: (Tuning) -> Unit) {
    GlassCard(gap = 14) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Hand signs", style = type(15, 500))
            Text("Hold about half a second", style = type(13, color = InkSoft))
        }
        ToggleRow("Hand signs in view of the camera", tuning.handGestures) { onChange(tuning.copy(handGestures = it)) }
        if (tuning.handGestures) {
            Text(describeHandReading(reading), style = type(15, color = InkSoft))
            HandSign.entries.forEach { sign ->
                ToggleRow("${sign.symbol}  ${sign.meaning}", sign in tuning.handSigns) { on ->
                    onChange(tuning.copy(handSigns = if (on) tuning.handSigns + sign else tuning.handSigns - sign))
                }
            }
        }
    }
}

private val FieldSurface = SurfaceStyle(
    base = rgba(255, 255, 255, 0.7f),
    border = BorderStroke(1.dp, rgba(255, 255, 255, 0.95f)),
    shadows = listOf(BoxShadow(y = 2.dp, blur = 6.dp, color = rgba(80, 90, 100, 0.12f), inset = true)),
)

private fun Float.two() = "%.2f".format(Locale.US, this)

private fun Float.roundTo(step: Int): Long = (this / step).roundToLong() * step
