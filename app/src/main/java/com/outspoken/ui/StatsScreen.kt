package com.outspoken.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkOnModel
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.OutspokenTheme
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/** Null values are not measured yet and show as "-". */
data class StatsUi(
    val replyTimeSeconds: Float?,
    val tokensPerSecond: Float?,
    val repliesWritten: Int,
    val phoneTempCelsius: Float?,
    val modelName: String,
    val runtime: String,
    val blinkAccuracyPercent: Float?,
    val sessionMillis: Long,
    val sentencesSpoken: Int,
)

@Composable
fun StatsScreen(ui: StatsUi, onBack: () -> Unit, onAsk: ((String) -> Unit)? = null) {
    DesignScreen(Modifier.dottedCanvas(), gap = 16.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Pill(Surfaces.OfflinePill) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                Text("No internet used", style = type(15, 500))
            }
        }
        ScreenHeading(title = "This session", subtitle = "Measured on this phone while you talked.", gap = 6.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .surface(Surfaces.Model, RoundedCornerShape(30.dp))
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("On-device model", style = type(15, 500))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModelStat("Reply time", formatSeconds(ui.replyTimeSeconds), Modifier.weight(1f))
                    ModelStat("Model speed", formatWhole(ui.tokensPerSecond, " tok/s"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModelStat("Replies written", ui.repliesWritten.toString(), Modifier.weight(1f))
                    ModelStat("Phone temperature", formatWhole(ui.phoneTempCelsius, " °C"), Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(ui.modelName)
                Chip(ui.runtime)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Blink accuracy", formatWhole(ui.blinkAccuracyPercent, "%"), Modifier.weight(1f))
            StatTile("Session length", formatClock(ui.sessionMillis), Modifier.weight(1f))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .surface(Surfaces.Pink, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sentences spoken", style = type(14))
                Text(ui.sentencesSpoken.toString(), style = type(44, 300, lineHeight = 1f))
            }
            Text(
                "Each one chosen with a single blink",
                style = type(14, align = TextAlign.End),
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
        // Not in the design yet (listed as a design gap): the quick topics, so a question can be
        // asked here and the numbers above watched as the model answers it (owner request).
        onAsk?.let { QuickTopicRow(it) }
        Spacer(Modifier.weight(1f))
        FooterNote("Nothing leaves this phone.")
    }
}

@Composable
private fun ModelStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = type(13, color = InkOnModel))
        Text(value, style = type(30, 300, lineHeight = 1.1f))
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        Modifier
            .height(36.dp)
            .surface(Surfaces.Chip, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = type(14, 500))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .surface(Surfaces.Glass, RoundedCornerShape(26.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = type(13, color = InkSoft))
        Text(value, style = type(34, 300, lineHeight = 1.1f))
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun StatsPreview() {
    OutspokenTheme {
        StatsScreen(
            StatsUi(
                replyTimeSeconds = 1.4f,
                tokensPerSecond = 24f,
                repliesWritten = 12,
                phoneTempCelsius = 38f,
                modelName = "Gemma",
                runtime = "LiteRT-LM",
                blinkAccuracyPercent = 92f,
                sessionMillis = 252_000,
                sentencesSpoken = 9,
            ),
            onBack = {},
        )
    }
}
