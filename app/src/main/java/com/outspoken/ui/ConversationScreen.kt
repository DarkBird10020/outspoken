package com.outspoken.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.conversation.Board
import com.outspoken.listen.QuickTopics
import com.outspoken.ui.theme.EyesFoundDot
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.OutspokenTheme
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/**
 * [highlighted] is a card id from [Board]: 0 to 3 for replies, or one of the two fixed cards.
 * [heard] is the listener's question, null when nothing was heard.
 */
data class ConversationUi(
    val faceFound: Boolean,
    val heard: String?,
    val replies: List<String>,
    val highlighted: Int,
)

@Composable
fun ConversationScreen(
    ui: ConversationUi,
    onPractice: () -> Unit,
    onStats: () -> Unit,
    onSelect: (Int) -> Unit,
    onTranscript: () -> Unit = {},
    eyeHint: String? = null,
    eyeView: (@Composable (Modifier) -> Unit)? = null,
    onAsk: ((String) -> Unit)? = null,
) {
    DesignScreen(Modifier.dottedCanvas(), gap = 14.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.faceFound) {
                DotPill("Eyes found", EyesFoundDot)
            } else {
                DotPill("Looking for you", InkFaint)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleIconButton(R.drawable.ic_eye, "Practice round", onPractice)
                CircleIconButton(R.drawable.ic_transcript, "Live transcript (Office Kit)", onTranscript)
                CircleIconButton(R.drawable.ic_stats, "Session stats", onStats)
            }
        }
        // Not in the design yet (listed as a design gap): the live camera with the eye dots, so the
        // person can see their eyes are being read while they choose.
        eyeView?.invoke(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        eyeHint?.let { Text(it, style = type(15, color = InkSoft)) }
        ui.heard?.let { HeardCard(it) }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.replies.forEachIndexed { index, reply ->
                ReplyCard(
                    text = reply,
                    highlighted = index == ui.highlighted,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FixedCard("More options", ui.highlighted == Board.MORE_OPTIONS, Modifier.weight(1f)) {
                onSelect(Board.MORE_OPTIONS)
            }
            FixedCard("Yes / No", ui.highlighted == Board.YES_NO, Modifier.weight(1f)) {
                onSelect(Board.YES_NO)
            }
        }
        // Not in the design yet (listed as a design gap): one-tap questions for the visitor when
        // the room is too loud for the microphone (PRD F7 fallback).
        onAsk?.let { ask ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickTopics.all.forEach { (label, question) ->
                    Text(
                        label,
                        style = type(15, 500),
                        modifier = Modifier
                            .surface(Surfaces.Glass, RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClickLabel = question) { ask(question) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
        FooterNote("Runs on this phone. No internet.")
    }
}

@Composable
private fun HeardCard(question: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Glass, RoundedCornerShape(28.dp))
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = InkSoft, modifier = Modifier.size(16.dp))
            Text("Heard", style = type(14, color = InkSoft))
        }
        Text(question, style = type(26, 500, lineHeight = 1.15f))
    }
}

@Composable
private fun ReplyCard(text: String, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .surface(if (highlighted) Surfaces.Pink else Surfaces.Glass, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 22.dp, end = if (highlighted) 16.dp else 22.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = type(24, 500), modifier = Modifier.weight(1f))
        if (highlighted) {
            Box(
                Modifier
                    .size(72.dp)
                    .surface(Surfaces.BlinkBadge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("Blink", style = type(14, 600))
            }
        }
    }
}

@Composable
private fun FixedCard(text: String, highlighted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(60.dp)
            .surface(if (highlighted) Surfaces.Pink else Surfaces.Glass, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = type(18, 500))
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ConversationPreview() {
    OutspokenTheme {
        ConversationScreen(
            ConversationUi(
                faceFound = true,
                heard = "Are you in pain?",
                replies = listOf("Yes, my back hurts", "A little, I can manage", "No pain right now", "Please call the nurse"),
                highlighted = 0,
            ),
            onPractice = {},
            onStats = {},
            onSelect = {},
        )
    }
}
