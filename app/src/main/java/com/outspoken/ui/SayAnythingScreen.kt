package com.outspoken.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import com.outspoken.R
import com.outspoken.conversation.SentenceBuilder
import com.outspoken.ui.theme.EyesFoundDot
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/** What "Say anything" shows; [completion] is the model's finished sentence, null until it answers. */
data class BuilderUi(
    val sentence: String,
    val wordCount: Int,
    val words: List<String>,
    val completion: String?,
    val canSpeak: Boolean,
)

/**
 * Say anything (designed): the sentence so far, the model's finished sentence, four next words and
 * More words, Delete and Speak. [highlighted] is a [SentenceBuilder] card id. Delete on an empty
 * sentence reads Exit, so the person can leave with their eyes (the design's only exit is the back
 * button).
 */
@Composable
fun SayAnythingScreen(
    ui: BuilderUi,
    highlighted: Int,
    onSelect: (Int) -> Unit,
    onExit: () -> Unit,
) {
    DesignScreen(Modifier.dottedCanvas(), gap = 14.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The bedside helper's way out; the person uses Exit.
            CircleIconButton(R.drawable.ic_back, "Back to talking", onExit)
            DotPill("Say anything", EyesFoundDot)
        }
        SentenceCard(ui)
        FinishCard(ui.completion, highlighted == SentenceBuilder.FINISH) { onSelect(SentenceBuilder.FINISH) }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.words.chunked(2).forEachIndexed { row, pair ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEachIndexed { column, word ->
                        val card = row * 2 + column
                        WordCard(word, highlighted == card, Modifier.weight(1f)) { onSelect(card) }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BottomButton("More words", highlighted == SentenceBuilder.MORE_WORDS, Surfaces.Glass, 16, 500, Modifier.weight(1f)) {
                onSelect(SentenceBuilder.MORE_WORDS)
            }
            BottomButton(if (ui.wordCount == 0) "Exit" else "Delete", highlighted == SentenceBuilder.DELETE, Surfaces.Glass, 16, 500, Modifier.weight(1f)) {
                onSelect(SentenceBuilder.DELETE)
            }
            BottomButton("Speak", highlighted == SentenceBuilder.SPEAK, Surfaces.Green, 18, 600, Modifier.weight(1f), enabled = ui.canSpeak) {
                onSelect(SentenceBuilder.SPEAK)
            }
        }
        FooterNote("Words come from the model on this phone.")
    }
}

@Composable
private fun SentenceCard(ui: BuilderUi) {
    Column(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Glass, RoundedCornerShape(28.dp))
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Your sentence", style = type(14, color = InkSoft))
            Text(if (ui.wordCount == 1) "1 word" else "${ui.wordCount} words", style = type(14, color = InkSoft))
        }
        val text = buildAnnotatedString {
            if (ui.sentence.isEmpty()) withStyle(type(30, 500, color = InkFaint).toSpanStyle()) { append("Pick a first word") } else append(ui.sentence)
            appendInlineContent(CARET, "|")
        }
        Text(
            text,
            style = type(30, 500, lineHeight = 1.15f),
            inlineContent = mapOf(
                CARET to InlineTextContent(Placeholder(9.sp, 30.sp, PlaceholderVerticalAlign.TextCenter)) {
                    Box(
                        Modifier
                            .padding(start = 6.dp)
                            .width(3.dp)
                            .fillMaxHeight()
                            .surface(SurfaceStyle(base = CaretPink), RoundedCornerShape(2.dp))
                    )
                },
            ),
        )
    }
}

private const val CARET = "caret"
private val CaretPink = Color(0xFFF04E88)

@Composable
private fun FinishCard(completion: String?, highlighted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .surface(if (highlighted) Surfaces.Pink else Surfaces.Model, shape)
            .clip(shape)
            .clickable(enabled = completion != null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Finish it for me", style = type(13))
            Text(completion ?: "Thinking of the rest…", style = type(20, 500, color = if (completion == null) InkSoft else Ink, lineHeight = 1.2f))
        }
        if (highlighted) BlinkBadge() else Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun WordCard(word: String, highlighted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxHeight()
            .surface(if (highlighted) Surfaces.Pink else Surfaces.Glass, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = if (highlighted) 12.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(word, style = type(26, 500), modifier = Modifier.weight(1f))
        if (highlighted) BlinkBadge()
    }
}

@Composable
private fun BlinkBadge() {
    Box(
        Modifier
            .size(58.dp)
            .surface(Surfaces.BlinkBadge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("Blink", style = type(13, 600))
    }
}

@Composable
private fun BottomButton(
    text: String,
    highlighted: Boolean,
    style: SurfaceStyle,
    size: Int,
    weight: Int,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .height(60.dp)
            .surface(if (highlighted) Surfaces.Pink else style, shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = type(size, weight, color = if (enabled) Ink else InkFaint))
    }
}
