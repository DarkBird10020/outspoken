package com.outspoken.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.suggest.Turn
import com.outspoken.ui.theme.EyesFoundDot
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/** State for the mirrored listener/laptop transcript screen (PRD S2). */
data class TranscriptUi(
    val turns: List<Turn>,
    val isListening: Boolean,
    val totalSentences: Int,
)

@Composable
fun TranscriptScreen(
    ui: TranscriptUi,
    onBack: () -> Unit,
) {
    DesignScreen(Modifier.dottedCanvas(), gap = 16.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Pill(Surfaces.OfflinePill) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                Text("Office Kit Mirror", style = type(15, 500))
            }
        }

        ScreenHeading(
            title = "Live transcript",
            subtitle = "Mirrored to laptop via Office Kit for listeners & caregivers.",
            gap = 6.dp,
        )

        if (ui.turns.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .surface(Surfaces.Glass, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_transcript),
                        contentDescription = null,
                        tint = InkSoft,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        "No conversation turns yet",
                        style = type(20, 500, color = InkSoft),
                    )
                    Text(
                        "Spoken visitor questions and chosen speaker replies will stream here in large text.",
                        style = type(15, color = InkFaint),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.turns) { turn ->
                    if (turn.fromListener) {
                        VisitorTurnCard(turn.text)
                    } else {
                        SpeakerTurnCard(turn.text)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.isListening) {
                DotPill("Listening for visitor", EyesFoundDot)
            } else {
                DotPill("Offline on-device", InkFaint)
            }
            Text("${ui.totalSentences} spoken", style = type(14, color = InkSoft))
        }

        FooterNote("iQOO Office Kit multi-screen collaboration. Zero internet.")
    }
}

@Composable
private fun VisitorTurnCard(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Glass, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = InkSoft,
                modifier = Modifier.size(16.dp),
            )
            Text("Visitor asked", style = type(13, color = InkSoft))
        }
        Text(text, style = type(22, 500, lineHeight = 1.25f))
    }
}

@Composable
private fun SpeakerTurnCard(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Pink, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_eye),
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(16.dp),
            )
            Text("Speaker answered", style = type(13, color = Ink))
        }
        Text(text, style = type(26, 600, lineHeight = 1.2f))
    }
}
