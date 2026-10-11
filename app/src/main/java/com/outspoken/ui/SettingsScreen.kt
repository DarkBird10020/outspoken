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
import androidx.compose.ui.text.input.KeyboardType
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
    /** The card language's name when the phone has no offline voice for it. */
    missingVoice: String?,
    onInstallVoice: () -> Unit,
    /** What the help alarm will do with the SOS contact, or why it cannot. */
    sosLine: String,
    onSaveSos: (typed: String) -> Unit,
    onTestSos: () -> Unit,
    whatsAppHelperOn: Boolean = false,
    onOpenAccessibility: () -> Unit = {},
    /** What the last test did, while Settings stays open. */
    sosTestResult: String? = null,
    handReading: HandReading,
    onTuningChange: (Tuning) -> Unit,
    onTuningReset: () -> Unit,
    onEyeReset: () -> Unit = {},
    onAsk: (String) -> Unit,
    onCalibrate: () -> Unit,
    onModels: () -> Unit,
    onBack: () -> Unit,
    onProfile: () -> Unit = {},
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
        LanguageCard(tuning, missingVoice, onInstallVoice, onTuningChange)
        SosCard(tuning, sosLine, onSaveSos, onTestSos, whatsAppHelperOn, onOpenAccessibility, sosTestResult, onTuningChange)
        TuningCard(tuning, onTuningChange, onEyeReset)
        HandCard(tuning, handReading, onTuningChange)
        // Not in the settings design (listed as a design gap): the way to the profile page.
        PillButton("Patient profile", onProfile, Modifier.fillMaxWidth())
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
                    .surface(Surfaces.Field, RoundedCornerShape(26.dp))
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
private fun TuningCard(tuning: Tuning, onChange: (Tuning) -> Unit, onEyeReset: () -> Unit) {
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
        // Not in the design (a design gap): undo slider changes without losing languages or SOS.
        PillButton("Reset eye settings", onEyeReset, Modifier.fillMaxWidth())
    }
}

/**
 * Hand signs (not in the design, listed as a design gap): on or off, what the camera reads now,
 * and each sign on or off for this person.
 */
/** Not in the design (a design gap): who the help alarm texts and calls. */
@Composable
private fun SosCard(
    tuning: Tuning,
    sosLine: String,
    onSave: (String) -> Unit,
    onTest: () -> Unit,
    whatsAppHelperOn: Boolean,
    onOpenAccessibility: () -> Unit,
    testResult: String?,
    onChange: (Tuning) -> Unit,
) {
    var typed by remember(tuning.sosNumber) { mutableStateOf(tuning.sosNumber) }
    GlassCard(gap = 12) {
        Text("SOS contact", style = type(15, 500))
        Text(sosLine, style = type(13, color = InkSoft))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .surface(Surfaces.Field, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (typed.isEmpty()) Text("Phone number", style = type(16, color = InkFaint))
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    textStyle = type(16),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(typed) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "SOS phone number" },
                )
            }
            PillButton("Save", { onSave(typed) }, Modifier.width(72.dp), style = Surfaces.StartButton, height = 52, size = 16, weight = 600)
        }
        // Not in the design (a design gap): the PRD's confirm close; off, sleep could call for help.
        ToggleRow("Help: after the 2 s hold, a blink to confirm", tuning.helpConfirm) { onChange(tuning.copy(helpConfirm = it)) }
        ToggleRow("Text them when help is called", tuning.sosText) { onChange(tuning.copy(sosText = it)) }
        ToggleRow("Then call them", tuning.sosCall) { onChange(tuning.copy(sosCall = it)) }
        // Not in the design (a design gap): WhatsApp for a phone with no SIM.
        ToggleRow("No SIM: WhatsApp message instead", tuning.sosWhatsApp) { onChange(tuning.copy(sosWhatsApp = it)) }
        ToggleRow("No SIM: then a WhatsApp call", tuning.sosWhatsAppCall) { onChange(tuning.copy(sosWhatsAppCall = it)) }
        if ((tuning.sosWhatsApp || tuning.sosWhatsAppCall) && !whatsAppHelperOn) {
            PillButton("Turn on Outspoken in Accessibility", onOpenAccessibility, Modifier.fillMaxWidth())
        }
        if (tuning.sosNumber.isNotEmpty()) PillButton("Test: text and call now", onTest, Modifier.fillMaxWidth())
        // Not in the design (a design gap): what the test did.
        if (testResult != null) Text("Test: $testResult", style = type(13, color = InkSoft))
    }
}

/**
 * Not in the design (a design gap): the person's languages and the one the app starts in. The
 * cards, the voice, listening and the model follow whichever of them the visitor speaks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageCard(tuning: Tuning, missingVoice: String?, onInstallVoice: () -> Unit, onChange: (Tuning) -> Unit) {
    val chosen = tuning.chosenLanguages
    GlassCard(gap = 14) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Languages", style = type(15, 500))
            Text("${chosen.size} chosen", style = type(13, color = InkSoft))
        }
        Text("Tap every language spoken around the person. Its speech pack and voice download on the phone.", style = type(13, color = InkSoft))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { language ->
                val on = language in chosen
                PillButton(
                    if (language.label == language.englishName) language.label else "${language.label} · ${language.englishName.substringBefore(" (")}",
                    {
                        val next = if (on) chosen - language else chosen + language
                        // At least one language stays; the starting one moves to the first left.
                        if (next.isNotEmpty()) {
                            val start = if (tuning.language in next) tuning.language else next.first()
                            onChange(tuning.copy(language = start, languages = next))
                        }
                    },
                    style = if (on) Surfaces.OfflinePill else Surfaces.Glass,
                    height = 40,
                    size = 14,
                    weight = if (on) 600 else 500,
                    sidePadding = 14,
                )
            }
        }
        if (chosen.size > 1) {
            Text("Start in", style = type(13, color = InkSoft))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chosen.forEach { language ->
                    PillButton(
                        language.label,
                        { onChange(tuning.copy(language = language)) },
                        style = if (language == tuning.language) Surfaces.StartButton else Surfaces.Glass,
                        height = 40,
                        size = 14,
                        weight = if (language == tuning.language) 600 else 500,
                        sidePadding = 14,
                    )
                }
            }
            ToggleRow("Follow the visitor: cards switch to the language they speak", tuning.autoLanguage) {
                onChange(tuning.copy(autoLanguage = it))
            }
        }
        if (missingVoice != null) {
            Text("No offline $missingVoice voice on this phone yet", style = type(13, color = InkSoft))
            PillButton("Download the $missingVoice voice", onInstallVoice, Modifier.fillMaxWidth())
        }
    }
}

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


private fun Float.two() = "%.2f".format(Locale.US, this)

private fun Float.roundTo(step: Int): Long = (this / step).roundToLong() * step
