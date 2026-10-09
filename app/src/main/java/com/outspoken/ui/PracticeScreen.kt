package com.outspoken.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.ui.theme.BoxShadow
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.OutspokenTheme
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.rgba
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/**
 * [starAt] is which of the three targets holds the star. [eyeOpen] and [blinkLevel] run from
 * 0 to 1 across the bar. [eyeState] and [progressNote] are short status lines, for example
 * "Steady" and "One more and you are ready".
 */
data class PracticeUi(
    val starAt: Int,
    val caught: Int,
    val needed: Int,
    val progressNote: String,
    val eyeOpen: Float,
    val blinkLevel: Float,
    val eyeState: String,
    val holdTimeSeconds: Float,
    val scanSpeedSeconds: Float,
)

private val BarFill = SurfaceStyle(
    base = Color.White,
    shadows = listOf(BoxShadow(y = 2.dp, blur = 8.dp, color = rgba(20, 90, 40, 0.35f))),
)

@Composable
fun PracticeScreen(ui: PracticeUi, onBack: () -> Unit, onStart: () -> Unit) {
    DesignScreen(Modifier.dottedCanvas(), gap = 12.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Pill(Surfaces.Glass) { Text("Practice round", style = type(15, 500)) }
        }
        ScreenHeading(
            title = "Blink when the star lights up",
            subtitle = "Close your eyes for about half a second, then open them.",
            gap = 6.dp,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { if (it == ui.starAt) StarTarget() else EmptyTarget() }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .surface(Surfaces.Glass, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Blinks caught", style = type(14, color = InkSoft))
                Text("${ui.caught} of ${ui.needed}", style = type(44, 300, lineHeight = 1f))
            }
            Text(
                ui.progressNote,
                style = type(14, color = InkSoft, align = TextAlign.End),
                modifier = Modifier.widthIn(max = 130.dp),
            )
        }
        EyeLevelCard(ui)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingTile("Hold time", formatSeconds(ui.holdTimeSeconds), Modifier.weight(1f))
            SettingTile("Scan speed", formatSeconds(ui.scanSpeedSeconds), Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .surface(Surfaces.StartButton, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .clickable(role = Role.Button, onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Text("Start talking", style = type(20, 600))
        }
    }
}

@Composable
private fun EmptyTarget() {
    Box(
        Modifier
            .size(96.dp)
            .surface(Surfaces.Glass, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_ring), contentDescription = null, tint = InkFaint, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun StarTarget() {
    Box(
        Modifier
            .size(124.dp)
            .surface(Surfaces.Star, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_star), contentDescription = "Star", tint = Ink, modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun EyeLevelCard(ui: PracticeUi) {
    Column(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Green, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Eyes open", style = type(15, 500))
            Text(ui.eyeState, style = type(15, 500))
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .surface(SurfaceStyle(base = rgba(255, 255, 255, 0.45f)), RoundedCornerShape(6.dp))
        ) {
            Box(
                Modifier
                    .width(maxWidth * ui.eyeOpen.coerceIn(0f, 1f))
                    .height(12.dp)
                    .surface(BarFill, RoundedCornerShape(6.dp))
            )
            Box(
                Modifier
                    .offset(x = maxWidth * ui.blinkLevel.coerceIn(0f, 1f), y = (-5).dp)
                    .size(width = 2.dp, height = 22.dp)
                    .surface(SurfaceStyle(base = Ink), RoundedCornerShape(0.dp))
            )
        }
        Text("The line is your blink level, set from your own eyes.", style = type(13))
    }
}

@Composable
private fun SettingTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .surface(Surfaces.Glass, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = type(13, color = InkSoft))
        Text(value, style = type(22, 500))
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PracticePreview() {
    OutspokenTheme {
        PracticeScreen(
            PracticeUi(
                starAt = 1,
                caught = 2,
                needed = 3,
                progressNote = "One more and you are ready",
                eyeOpen = 0.78f,
                blinkLevel = 0.32f,
                eyeState = "Steady",
                holdTimeSeconds = 0.5f,
                scanSpeedSeconds = 1.2f,
            ),
            onBack = {},
            onStart = {},
        )
    }
}
