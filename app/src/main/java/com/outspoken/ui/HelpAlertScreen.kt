package com.outspoken.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkOnHelp
import com.outspoken.ui.theme.OutspokenTheme
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

@Composable
fun HelpAlertScreen(
    lastSaid: String,
    onSoundOff: () -> Unit,
    onDismiss: () -> Unit,
    /** Who the alarm texted and called ("Texting and calling …3210"), when an SOS contact is set. */
    sosLine: String? = null,
) {
    DesignScreen(Modifier.surface(Surfaces.HelpBackground, RectangleShape), gap = 18.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DotPill("Alarm is sounding", Ink, Surfaces.HelpGlass)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(176.dp)
                    .surface(Surfaces.HelpBellRing, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(124.dp)
                        .surface(Surfaces.HelpBell, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = Ink, modifier = Modifier.size(56.dp))
                }
            }
            Text("Help needed", style = type(54, 500, lineHeight = 1f, align = TextAlign.Center))
            Text(
                "Eyes were held shut for 2 seconds, then confirmed with a blink.",
                style = type(19, lineHeight = 1.35f, align = TextAlign.Center),
                modifier = Modifier.widthIn(max = 280.dp),
            )
            // Not in the design (a design gap): the SOS contact being reached.
            sosLine?.let { Text(it, style = type(19, 600, align = TextAlign.Center)) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .surface(Surfaces.HelpGlass, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Last thing said", style = type(13, color = InkOnHelp))
                Text(lastSaid, style = type(19, 500))
            }
            Box(
                Modifier
                    .size(52.dp)
                    .surface(Surfaces.HelpMute, CircleShape)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onSoundOff),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_sound_off),
                    contentDescription = "Turn alarm sound off",
                    tint = Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .surface(Surfaces.HelpDismiss, RoundedCornerShape(36.dp))
                .clip(RoundedCornerShape(36.dp))
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("I am here", style = type(22, 600))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HelpAlertPreview() {
    OutspokenTheme {
        HelpAlertScreen(lastSaid = "Yes, my back hurts", onSoundOff = {}, onDismiss = {})
    }
}
