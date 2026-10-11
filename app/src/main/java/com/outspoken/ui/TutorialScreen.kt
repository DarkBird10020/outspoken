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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.tutorial.Audience
import com.outspoken.tutorial.HelpPracticeUi
import com.outspoken.tutorial.HelpStage
import com.outspoken.tutorial.Lesson
import com.outspoken.tutorial.Lessons
import com.outspoken.tutorial.MovePracticeUi
import com.outspoken.ui.theme.EyesFoundDot
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
import java.util.Locale

/** Every lesson, grouped by who it is for, with a tick on the finished ones. */
@Composable
fun TutorialScreen(
    lessons: List<Lesson>,
    finished: Set<String>,
    onOpen: (Lesson) -> Unit,
    onBack: () -> Unit,
) {
    val done = lessons.count { it.id in finished }
    DesignScreen(Modifier.dottedCanvas(), gap = 12.dp, scrolls = true) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Pill(Surfaces.Glass) { Text("Lessons", style = type(15, 500)) }
        }
        ScreenHeading(
            title = "Learn Outspoken",
            subtitle = "$done of ${lessons.size} lessons finished. The first time, go through them in order.",
            gap = 6.dp,
        )
        ProgressBar(if (lessons.isEmpty()) 0f else done.toFloat() / lessons.size)
        val next = Lessons.nextUnfinished(lessons, finished)
        if (next != null) {
            PillButton(
                "Continue: ${next.title}",
                { onOpen(next) },
                Modifier.fillMaxWidth(),
                style = Surfaces.StartButton,
                weight = 600,
                sidePadding = 16,
            )
        } else {
            Text("All lessons finished. Open any lesson to go over it again.", style = type(15, color = InkSoft))
        }
        Audience.entries.forEach { audience ->
            val group = lessons.filter { it.audience == audience }
            if (group.isNotEmpty()) {
                Text(audience.label, style = type(15, 500), modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                group.forEach { lesson ->
                    LessonRow(lessons.indexOf(lesson) + 1, lesson, lesson.id in finished) { onOpen(lesson) }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(number: Int, lesson: Lesson, finished: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Glass, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "Open lesson", role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .surface(if (finished) Surfaces.OfflinePill else Surfaces.Glass, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (finished) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = "Finished", tint = Ink, modifier = Modifier.size(20.dp))
            } else {
                Text("$number", style = type(16, 600))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lesson.title, style = type(18, 500))
            Text(lesson.summary, style = type(14, color = InkSoft, lineHeight = 1.3f))
            Text(
                "${lesson.steps.size} steps" + (lesson.practice?.let { " · practice with the camera" } ?: ""),
                style = type(13, color = InkFaint),
            )
        }
    }
}

/** One lesson, a step at a time. [step] is 0-based. */
@Composable
fun LessonScreen(
    lesson: Lesson,
    number: Int,
    total: Int,
    step: Int,
    onStep: (Int) -> Unit,
    onReadAloud: () -> Unit,
    onPractice: () -> Unit,
    onOpenPage: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val current = lesson.steps[step]
    val last = step == lesson.steps.lastIndex
    DesignScreen(Modifier.dottedCanvas(), gap = 12.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(R.drawable.ic_back, "Back to lessons", onBack)
            Pill(Surfaces.Glass) { Text("Lesson $number of $total", style = type(15, 500)) }
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lesson.audience.label, style = type(14, color = InkSoft))
            Text(lesson.title, style = type(22, 500))
        }
        StepBar(step, lesson.steps.size)
        Text("Step ${step + 1} of ${lesson.steps.size}", style = type(13, color = InkSoft), modifier = Modifier.padding(start = 4.dp))
        GlassCard(Modifier.weight(1f), gap = 14) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(current.title, style = type(28, 400, lineHeight = 1.1f))
                Text(current.body, style = type(18, lineHeight = 1.45f))
            }
        }
        PillButton("Read aloud", onReadAloud, Modifier.fillMaxWidth())
        if (last) lesson.opens?.let { PillButton(it.label, onOpenPage, Modifier.fillMaxWidth()) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                if (step == 0) "All lessons" else "Back",
                { if (step == 0) onBack() else onStep(step - 1) },
                Modifier.weight(1f),
            )
            PillButton(
                when {
                    !last -> "Next"
                    lesson.practice != null -> lesson.practice.label
                    else -> "Finish lesson"
                },
                {
                    when {
                        !last -> onStep(step + 1)
                        lesson.practice != null -> onPractice()
                        else -> onFinish()
                    }
                },
                Modifier.weight(1f),
                style = Surfaces.StartButton,
                weight = 600,
                size = if (last && lesson.practice != null) 15 else 17,
            )
        }
    }
}

/** The "Moving the highlight" practice: four cards, a star on the one to reach. */
@Composable
fun MovePracticeScreen(ui: MovePracticeUi, hint: String, onBack: () -> Unit, onAgain: () -> Unit, onDone: () -> Unit) {
    DesignScreen(Modifier.dottedCanvas(), gap = 12.dp) {
        PracticeTopBar("Practice: moving", ui.faceFound, onBack)
        ScreenHeading(
            title = if (ui.done) "All three reached" else "Move to the star",
            subtitle = ui.note.ifEmpty { hint },
            gap = 6.dp,
        )
        ui.cards.forEachIndexed { index, text ->
            PracticeCard(text, highlighted = index == ui.highlighted, star = index == ui.target)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Stars reached", "${ui.reached} of ${ui.needed}", Modifier.weight(1f))
            Tile("Wrong choices", "${ui.wrong}", Modifier.weight(1f))
        }
        Text(hint, style = type(14, color = InkSoft), modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.weight(1f))
        if (ui.done) PracticeDoneButtons(onAgain, onDone)
    }
}

@Composable
private fun PracticeCard(text: String, highlighted: Boolean, star: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .surface(if (highlighted) Surfaces.Pink else Surfaces.Glass, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = type(22, if (highlighted) 600 else 400))
        if (star) {
            Box(
                Modifier
                    .size(40.dp)
                    .surface(Surfaces.Star, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_star), contentDescription = "Star", tint = Ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The "Calling for help" practice: the two steps, and how far through the hold the eyes are. */
@Composable
fun HelpPracticeScreen(ui: HelpPracticeUi, onBack: () -> Unit, onAgain: () -> Unit, onDone: () -> Unit) {
    DesignScreen(Modifier.dottedCanvas(), gap = 12.dp) {
        PracticeTopBar("Practice: help", ui.faceFound, onBack)
        ScreenHeading(
            title = when (ui.stage) {
                HelpStage.CloseEyes -> "Hold your eyes shut"
                HelpStage.OpenAndBlink -> "Open, then blink"
                HelpStage.Done -> "Help would be called"
            },
            subtitle = ui.note,
            gap = 6.dp,
        )
        GlassCard(gap = 12) {
            HelpStepRow(1, "Keep both eyes shut for two seconds, until the beep", ui.stage != HelpStage.CloseEyes)
            HelpStepRow(2, "Open your eyes, then blink once within five seconds", ui.stage == HelpStage.Done)
        }
        GlassCard(style = Surfaces.Green, gap = 12) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hold", style = type(15, 500))
                Text(String.format(Locale.US, "%.1f of 2 s", ui.holdProgress * 2), style = type(15, 500))
            }
            Bar(ui.holdProgress)
        }
        Spacer(Modifier.weight(1f))
        FooterNote("Practice only: no alarm, no texts, no calls.")
        if (ui.stage == HelpStage.Done) PracticeDoneButtons(onAgain, onDone)
    }
}

@Composable
private fun HelpStepRow(number: Int, text: String, done: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .surface(if (done) Surfaces.OfflinePill else Surfaces.Glass, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = "Done", tint = Ink, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = type(15, 600))
            }
        }
        Text(text, style = type(16, lineHeight = 1.3f), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PracticeTopBar(title: String, faceFound: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(R.drawable.ic_back, "Back to the lesson", onBack)
        DotPill(if (faceFound) title else "Looking for you", if (faceFound) EyesFoundDot else InkFaint)
    }
}

@Composable
private fun PracticeDoneButtons(onAgain: () -> Unit, onDone: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Practise again", onAgain, Modifier.weight(1f))
        PillButton("Back to lessons", onDone, Modifier.weight(1f), style = Surfaces.StartButton, weight = 600)
    }
}

@Composable
private fun StepBar(step: Int, steps: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(steps) {
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .surface(if (it <= step) Surfaces.OfflinePill else Surfaces.Glass, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun ProgressBar(share: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Glass, RoundedCornerShape(10.dp))
            .padding(4.dp)
    ) {
        Bar(share)
    }
}

@Composable
private fun Bar(share: Float) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .surface(SurfaceStyle(base = rgba(255, 255, 255, 0.45f)), RoundedCornerShape(6.dp))
    ) {
        Box(
            Modifier
                .width(maxWidth * share.coerceIn(0f, 1f))
                .height(12.dp)
                .surface(SurfaceStyle(base = Color.White), RoundedCornerShape(6.dp))
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TutorialPreview() {
    OutspokenTheme {
        TutorialScreen(Lessons.all(), finished = setOf("welcome", "setup"), onOpen = {}, onBack = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LessonPreview() {
    OutspokenTheme {
        LessonScreen(Lessons.all()[3], 4, 17, step = 1, onStep = {}, onReadAloud = {}, onPractice = {}, onOpenPage = {}, onFinish = {}, onBack = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MovePracticePreview() {
    OutspokenTheme {
        MovePracticeScreen(
            MovePracticeUi(
                cards = listOf("I need water", "I am in pain", "Yes", "Thank you"),
                highlighted = 1,
                target = 2,
                reached = 1,
                needed = 3,
                wrong = 0,
                note = "Well done. Now move to \"Yes\".",
                faceFound = true,
            ),
            hint = "Look up to move to the next card. Close both eyes: choose.",
            onBack = {},
            onAgain = {},
            onDone = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HelpPracticePreview() {
    OutspokenTheme {
        HelpPracticeScreen(
            HelpPracticeUi(HelpStage.CloseEyes, 0.6f, "Close your eyes and keep them closed until you hear the beep.", faceFound = true),
            onBack = {},
            onAgain = {},
            onDone = {},
        )
    }
}
