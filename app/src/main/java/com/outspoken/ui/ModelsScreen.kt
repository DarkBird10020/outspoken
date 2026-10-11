package com.outspoken.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.setup.ModelChoice
import com.outspoken.setup.VoicePack
import com.outspoken.speech.KokoroVoice
import com.outspoken.speech.NaturalVoices
import com.outspoken.suggest.ModelState
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.rgba
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/** Numbers the Model screen shows; null where nothing is measured yet. */
data class ModelPageUi(
    val state: ModelState,
    val modelLine: String,
    val replySeconds: Float?,
    val tokensPerSecond: Float?,
    val offlineVoice: Boolean?,
    val buildLine: String,
)

/**
 * The voice section of the Model screen. [naturalLine] says where the natural voice is up to
 * (not downloaded, downloading, unpacking 40%, loading, ready). [naturalReady] means unpacked and
 * usable; [busy] means unpacking or loading, when its button does nothing.
 */
data class VoicePageUi(
    val naturalInUse: Boolean,
    val naturalReady: Boolean,
    val busy: Boolean,
    val naturalLine: String,
    val voice: KokoroVoice,
    val speed: Float,
)

/**
 * Model (designed): the Gemma models with Download, Use or In use, picking a model file, reply
 * speed, the offline voice, which build this is, and sending the logs.
 */
@Composable
fun ModelsScreen(
    ui: ModelPageUi,
    models: List<ModelRow>,
    canSeeDownloads: Boolean,
    onAllowDownloads: () -> Unit,
    onDownload: (ModelChoice) -> Unit,
    onUse: (ModelChoice) -> Unit,
    onChooseFile: () -> Unit,
    onShareLogs: () -> Unit,
    onSaveLogs: () -> Unit,
    onBack: () -> Unit,
    voice: VoicePageUi,
    onPhoneVoice: () -> Unit,
    onNaturalVoice: () -> Unit,
    onPickVoice: (KokoroVoice) -> Unit,
    onSample: (KokoroVoice) -> Unit,
    onSpeed: (Float) -> Unit,
) {
    DesignScreen(Modifier.dottedCanvas(), gap = 14.dp, scrolls = true) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(R.drawable.ic_back, "Back to talking", onBack)
            ModelStatePill(ui.state)
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Model on this phone", style = type(34, 400, lineHeight = 1.08f))
            Text("It writes the replies. Nothing is sent anywhere.", style = type(16, color = InkSoft, lineHeight = 1.35f))
        }
        // Not in the design: why a model is missing or failed, in plain words.
        if (ui.state !is ModelState.Ready) Text(ui.modelLine, style = type(14, color = InkSoft), modifier = Modifier.padding(horizontal = 4.dp))
        if (!canSeeDownloads) {
            PillButton("Allow access to Downloads", onAllowDownloads, Modifier.fillMaxWidth(), style = Surfaces.StartButton, weight = 600)
        }
        models.forEach { row -> ModelCard(row, onDownload, onUse) }
        PillButton("Choose a model file", onChooseFile, Modifier.fillMaxWidth())
        VoiceSection(voice, onPhoneVoice, onNaturalVoice, onPickVoice, onSample, onSpeed)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Last replies", ui.replySeconds?.let { formatSeconds(it) } ?: "-", Modifier.weight(1f))
            Tile("Model speed", ui.tokensPerSecond?.let { "${it.toInt()} tok/s" } ?: "-", Modifier.weight(1f))
        }
        GlassCard(gap = 12) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Offline voice", style = type(13, color = InkSoft))
                    Text(
                        when (ui.offlineVoice) {
                            null -> "Checking"
                            true -> "Ready"
                            false -> "Missing"
                        },
                        style = type(18, 500),
                    )
                }
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                    Text("Build", style = type(13, color = InkSoft))
                    Text(ui.buildLine.substringBefore(' '), style = type(18, 500))
                }
            }
            // Not in the design: whether this APK can install over the others (signing key).
            Text(ui.buildLine.substringAfter(' ', "").replaceFirstChar { it.uppercase() }, style = type(13, color = InkFaint))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Share logs", onShareLogs, Modifier.weight(1f))
            PillButton("Save logs", onSaveLogs, Modifier.weight(1f))
        }
        FooterNote("The download opens in the phone's browser.")
    }
}

@Composable
private fun ModelStatePill(state: ModelState) {
    when (state) {
        is ModelState.Ready -> Pill(Surfaces.OfflinePill) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Text("Ready on ${state.backend.substringBefore(' ')}", style = type(15, 500))
        }
        is ModelState.Loading -> DotPill("Loading", InkSoft)
        is ModelState.Failed -> DotPill("Could not load", Ink, Surfaces.Pink)
        ModelState.Missing -> DotPill("No model yet", InkFaint)
    }
}

@Composable
private fun ModelCard(row: ModelRow, onDownload: (ModelChoice) -> Unit, onUse: (ModelChoice) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .surface(if (row.inUse) Surfaces.Model else Surfaces.Glass, RoundedCornerShape(28.dp))
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.choice.title, style = type(20, 500))
            Text("${row.choice.sizeGb}. ${row.choice.note.replaceFirstChar { it.uppercase() }}", style = type(14))
            Text(row.status.replaceFirstChar { it.uppercase() }, style = type(13, color = StatusInk))
        }
        val (label, action) = when {
            row.inUse -> "In use" to {}
            row.downloaded -> "Use" to { onUse(row.choice) }
            else -> "Download" to { onDownload(row.choice) }
        }
        PillButton(label, action, style = ModelButton, height = 48, size = 15, weight = 600, sidePadding = 18)
    }
}

@Composable
private fun VoiceSection(
    ui: VoicePageUi,
    onPhoneVoice: () -> Unit,
    onNaturalVoice: () -> Unit,
    onPickVoice: (KokoroVoice) -> Unit,
    onSample: (KokoroVoice) -> Unit,
    onSpeed: (Float) -> Unit,
) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Voice", style = type(24, 400))
        Text("How the chosen cards are said out loud.", style = type(15, color = InkSoft))
    }
    ChoiceCard(
        title = "Phone voice",
        line = "Built in. Every language, the help alarm and the lessons.",
        status = if (ui.naturalInUse) "ready" else "in use",
        inUse = !ui.naturalInUse,
        button = if (ui.naturalInUse) "Use" else "In use",
        onClick = onPhoneVoice,
    )
    ChoiceCard(
        title = VoicePack.TITLE,
        line = "${VoicePack.sizeMb}. ${VoicePack.NOTE.replaceFirstChar { it.uppercase() }}",
        status = ui.naturalLine,
        inUse = ui.naturalInUse,
        button = when {
            ui.naturalInUse -> "In use"
            ui.busy -> "Wait"
            ui.naturalReady -> "Use"
            else -> "Download"
        },
        onClick = onNaturalVoice,
    )
    if (ui.naturalReady) {
        GlassCard(gap = 10) {
            Text("Natural voice", style = type(15, 500))
            Text(
                "English cards use the voice picked here. Cards in other languages use the phone voice; try a Hindi voice with Play.",
                style = type(13, color = InkSoft, lineHeight = 1.3f),
            )
            NaturalVoices.all.forEach { v ->
                VoiceRow(v, chosen = v.id == ui.voice.id, onPick = { onPickVoice(v) }, onPlay = { onSample(v) })
            }
            DesignSlider("Speed", String.format(java.util.Locale.US, "%.2fx", ui.speed), ui.speed, 0.8f..1.2f) {
                onSpeed((it * 20).toInt() / 20f)
            }
        }
    }
}

@Composable
private fun VoiceRow(voice: KokoroVoice, chosen: Boolean, onPick: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(
            voice.label,
            onPick,
            Modifier.weight(1f),
            style = if (chosen) Surfaces.OfflinePill else Surfaces.Glass,
            height = 44,
            size = 15,
            weight = if (chosen) 600 else 400,
        )
        PillButton("Play", onPlay, style = ModelButton, height = 44, size = 15, weight = 600, sidePadding = 16)
    }
}

@Composable
private fun ChoiceCard(title: String, line: String, status: String, inUse: Boolean, button: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .surface(if (inUse) Surfaces.Model else Surfaces.Glass, RoundedCornerShape(28.dp))
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type(20, 500))
            Text(line, style = type(14))
            Text(status.replaceFirstChar { it.uppercase() }, style = type(13, color = StatusInk))
        }
        PillButton(button, onClick, style = ModelButton, height = 48, size = 15, weight = 600, sidePadding = 18)
    }
}

private val StatusInk = Color(0xFF3A3D42)

private val ModelButton = SurfaceStyle(
    base = rgba(255, 255, 255, 0.75f),
    border = BorderStroke(1.dp, Color.White),
)
