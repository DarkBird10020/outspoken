package com.outspoken.tutorial

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.blink.Wink
import com.outspoken.blink.WinkDetector
import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog
import com.outspoken.scan.GazeStep
import com.outspoken.scan.GazeStepper
import com.outspoken.scan.Scanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [highlighted] is -1 while the phone speaks, as on the main page. */
data class MovePracticeUi(
    val cards: List<String>,
    val highlighted: Int,
    val target: Int,
    val reached: Int,
    val needed: Int,
    val wrong: Int,
    val note: String,
    val faceFound: Boolean,
) {
    val done get() = reached >= needed
}

/**
 * The "Moving the highlight" practice: a star marks a card, the person moves the highlight to it
 * the same way as on the main page (looks, winks, or the timed scan in Blink only) and closes
 * their eyes to choose it. Three stars reached finish it. Wrong choices are only counted.
 */
class MovePractice(
    private val detector: BlinkDetector,
    private val gaze: GazeStepper,
    private val log: EventLog = EventLog.None,
    private val speak: (String) -> Unit = {},
    private val scanner: Scanner = Scanner(),
    private val wink: WinkDetector = WinkDetector(log = log),
) {
    var moveByEyes = true

    /** With looking down off, a look up moves to the next card, as on the main page. */
    var upMovesNext = true
    var winks = false

    var scanMs: Long
        get() = scanner.intervalMs
        set(value) {
            scanner.intervalMs = value
        }

    private var cursor = 0
    private var previousCursor = 0
    private var cursorMovedMs = Long.MIN_VALUE
    private var reached = 0
    private var wrong = 0
    private var note = ""
    private var clockMs = 0L

    /** Looks and closes do nothing until then: the phone is speaking after a choice. */
    private var quietUntilMs = Long.MIN_VALUE

    private val target get() = TARGETS[reached.coerceAtMost(TARGETS.size - 1)]

    private val _ui = MutableStateFlow(ui(0L))
    val ui: StateFlow<MovePracticeUi> = _ui.asStateFlow()

    fun start(timeMs: Long) {
        val nowMs = advance(timeMs)
        reached = 0
        wrong = 0
        cursor = 0
        previousCursor = 0
        cursorMovedMs = Long.MIN_VALUE
        quietUntilMs = nowMs + SPEAK_PAUSE_MS
        scanner.restart(CARDS.indices.toList(), quietUntilMs)
        note = "Move to \"${CARDS[target]}\", then close your eyes."
        log.write("tutorial", "move practice started, ${if (moveByEyes) "looks" else "timed scan every $scanMs ms"}${if (winks) " and winks" else ""}")
        speak(note)
        publish(nowMs)
    }

    fun onSample(sample: EyeSample) {
        val nowMs = advance(sample.timeMs)
        when (val event = detector.onSample(sample)) {
            is BlinkEvent.Blink -> onBlink(event, nowMs)
            BlinkEvent.FaceFound -> scanner.resume(nowMs)
            BlinkEvent.FaceLost -> scanner.pause(nowMs)
            null -> Unit
        }
        if (moveByEyes && !done && nowMs >= quietUntilMs && detector.tracking) {
            val winked = wink.onSample(sample, detector.settings)?.takeIf { winks }
            if (winked != null) moveCursor(if (winked == Wink.Left) cursor + 1 else cursor - 1, nowMs)
            // Closing one eye shifts the gaze reading, so looks wait until both eyes are level again.
            if (wink.active || wink.oneEyeLower) gaze.pauseUntil(nowMs + AFTER_WINK_MS)
            val step = gaze.onSample(sample.gaze, !detector.eitherEyeShut(sample), nowMs, sample.irisY)
            if (step != null) moveCursor(if (step == GazeStep.Next || upMovesNext) cursor + 1 else cursor - 1, nowMs)
        }
        publish(nowMs)
    }

    private val done get() = reached >= TARGETS.size

    private fun onBlink(blink: BlinkEvent.Blink, nowMs: Long) {
        if (done || blink.startMs < quietUntilMs) return
        // The help hold never chooses, here or on the main page.
        if (blink.durationMs >= HELP_HOLD_MS) return
        val card = if (moveByEyes) cardAt(blink.startMs) else scanner.cardAt(blink.startMs) ?: return
        if (card == target) {
            reached++
            log.write("tutorial", "move practice: reached \"${CARDS[card]}\", $reached of ${TARGETS.size}")
            note = if (done) "Well done. You can move and choose with your eyes." else "Well done. Now move to \"${CARDS[target]}\"."
        } else {
            wrong++
            log.write("tutorial", "move practice: chose \"${CARDS[card]}\", the star was on \"${CARDS[target]}\"")
            note = "That was \"${CARDS[card]}\". Move to \"${CARDS[target]}\"."
        }
        speak(note)
        // As on the main page, the highlight starts again from the first card after a choice.
        moveCursor(0, nowMs)
        quietUntilMs = nowMs + SPEAK_PAUSE_MS
        scanner.restart(CARDS.indices.toList(), quietUntilMs)
        gaze.pauseUntil(quietUntilMs)
    }

    private fun moveCursor(position: Int, nowMs: Long) {
        previousCursor = cursor
        cursor = ((position % CARDS.size) + CARDS.size) % CARDS.size
        cursorMovedMs = nowMs
    }

    /** The card lit when the eyes shut; a look fired while they were closing does not count. */
    private fun cardAt(timeMs: Long) = if (cursorMovedMs > timeMs) previousCursor else cursor

    private fun advance(timeMs: Long): Long {
        clockMs = maxOf(clockMs, timeMs)
        return clockMs
    }

    private fun publish(nowMs: Long) {
        _ui.value = ui(nowMs)
    }

    private fun ui(nowMs: Long) = MovePracticeUi(
        cards = CARDS,
        highlighted = when {
            done || nowMs < quietUntilMs -> -1
            moveByEyes -> cursor
            else -> scanner.cardAt(nowMs) ?: -1
        },
        target = if (done) -1 else target,
        reached = reached,
        needed = TARGETS.size,
        wrong = wrong,
        note = note,
        faceFound = detector.tracking,
    )

    companion object {
        val CARDS = listOf("I need water", "I am in pain", "Yes", "Thank you")

        /** Each target needs a different number of moves from the first card. */
        val TARGETS = listOf(2, 3, 1)

        /** Time for the phone to say what happened before the eyes move anything again. */
        const val SPEAK_PAUSE_MS = 1_500L
        const val HELP_HOLD_MS = 2_000L
        private const val AFTER_WINK_MS = 800L
    }
}
