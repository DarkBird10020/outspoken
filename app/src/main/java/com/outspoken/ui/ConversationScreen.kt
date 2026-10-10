package com.outspoken.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay

/**
 * [highlighted] is a card id from [Board]: 0 to 3 for replies, or one of the two fixed cards.
 * [heard] is the listener's question, null when nothing was heard.
 */
data class ConversationUi(
    val faceFound: Boolean,
    val heard: String?,
    val replies: List<String>,
    val highlighted: Int,
    /** Set while "Say anything" is open; its screen shows instead of the reply cards. */
    val builder: BuilderUi? = null,
)

@Composable
fun ConversationScreen(
    ui: ConversationUi,
    onPractice: () -> Unit,
    onStats: () -> Unit,
    onSelect: (Int) -> Unit,
    onTranscript: () -> Unit = {},
    onEyeCheck: () -> Unit = {},
    eyeHint: String? = null,
    eyeView: (@Composable (Modifier) -> Unit)? = null,
    onAsk: ((String) -> Unit)? = null,
    liveStats: (@Composable () -> Unit)? = null,
    /** Words being heard now, before the sentence ends; shown in place of the last question. */
    hearing: String? = null,
    /** Whether the microphone is on, for the chip that switches it; null hides the chip. */
    micOn: Boolean? = null,
    onMic: () -> Unit = {},
    topics: List<Pair<String, String>> = QuickTopics.all,
) {
    DesignScreen(Modifier.dottedCanvas(), gap = 14.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pill = Modifier
                .weight(1f, fill = false)
                .padding(end = 10.dp)
            if (ui.faceFound) {
                DotPill("Eyes found", EyesFoundDot, modifier = pill)
            } else {
                // "Looking for you" does not fit beside four round buttons, even shrunk (phone, 08:22).
                DotPill("Looking…", InkFaint, modifier = pill)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleIconButton(R.drawable.ic_star, "Practice round", onPractice)
                CircleIconButton(R.drawable.ic_eye, "Eye check", onEyeCheck)
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
        // Not in the design yet (listed as a design gap): the model's numbers, live, so they can be
        // watched changing while the person talks (owner request).
        liveStats?.invoke()
        val live = hearing?.takeIf { it.isNotBlank() }
        if (live != null) HeardCard(live, "Hearing…") else ui.heard?.let { HeardCard(it, "Heard") }
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
        // "Say anything" is not in the main page design (listed as a design gap). It shares the
        // row, at 16 sp, so the reply cards keep their height.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FixedCard("More options", ui.highlighted == Board.MORE_OPTIONS, Modifier.weight(1f), size = 16) {
                onSelect(Board.MORE_OPTIONS)
            }
            FixedCard("Yes / No", ui.highlighted == Board.YES_NO, Modifier.weight(1f), size = 16) {
                onSelect(Board.YES_NO)
            }
            FixedCard("Say anything", ui.highlighted == Board.SAY_ANYTHING, Modifier.weight(1f), size = 16) {
                onSelect(Board.SAY_ANYTHING)
            }
        }
        // Not in the design yet (listed as a design gap): one-tap questions for the visitor when
        // the room is too loud for the microphone (PRD F7 fallback).
        val micChip: (@Composable () -> Unit)? = micOn?.let { on -> @Composable { MicChip(on, onMic) } }
        onAsk?.let { ask -> QuickTopicRow(ask, topics, leading = micChip) }
        FooterNote("Runs on this phone. No internet.")
    }
}

/** One-tap questions for the visitor, a row that scrolls sideways, after an optional [leading] chip. */
@Composable
fun QuickTopicRow(
    onAsk: (String) -> Unit,
    topics: List<Pair<String, String>> = QuickTopics.all,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        topics.forEach { (label, question) ->
            Text(
                label,
                style = type(15, 500),
                modifier = Modifier
                    .surface(Surfaces.Glass, RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClickLabel = question) { onAsk(question) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * A row of short values that [read] fills again every [refreshMs], spread across the width with
 * even gaps (owner's screenshot, 09:06: the values run together with dots between them). Only this
 * row redraws, and only when a value changes.
 */
@Composable
fun LiveStatsLine(refreshMs: Long, read: () -> List<String>) {
    val latestRead by rememberUpdatedState(read)
    var values by remember { mutableStateOf(read()) }
    LaunchedEffect(refreshMs) {
        while (true) {
            values = latestRead()
            delay(refreshMs)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        values.forEach { Text(it, style = type(14, color = InkSoft), maxLines = 1) }
    }
}

/**
 * The microphone switch for the person at the bedside (owner request: listening should go on until
 * they stop it). Not in the design, listed as a design gap: green while listening, glass when off.
 */
@Composable
private fun MicChip(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .surface(if (on) Surfaces.OfflinePill else Surfaces.Glass, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = if (on) "Stop listening" else "Start listening", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = InkSoft, modifier = Modifier.size(16.dp))
        Text(if (on) "Listening" else "Mic off", style = type(15, 500))
    }
}

@Composable
private fun HeardCard(question: String, label: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Glass, RoundedCornerShape(28.dp))
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = InkSoft, modifier = Modifier.size(16.dp))
            Text(label, style = type(14, color = InkSoft))
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
        // Each card gets a quarter of the space left, and a reply of two lines did not fit: the
        // second line was cut off ("Yes, please, let me see them.", owner's screenshot, 07:46).
        // The text shrinks until it fits; a reply that fits keeps the designed 24 sp.
        BasicText(
            text,
            style = type(24, 500),
            modifier = Modifier.weight(1f),
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 24.sp, stepSize = 1.sp),
        )
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
private fun FixedCard(text: String, highlighted: Boolean, modifier: Modifier, size: Int = 18, onClick: () -> Unit) {
    Box(
        modifier
            .height(60.dp)
            .surface(if (highlighted) Surfaces.Pink else Surfaces.Glass, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = type(size, 500), maxLines = 1)
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
