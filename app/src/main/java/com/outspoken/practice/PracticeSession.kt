package com.outspoken.practice

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog
import com.outspoken.ui.PracticeUi
import java.util.Locale

/**
 * Calibration and practice round (PRD F6). First it measures this person's open-eye level and
 * normal blink length while they look at the screen, then sets the detector from them. Then the
 * star lights up in one of three places at a time and the person blinks while it is lit.
 */
class PracticeSession(
    private val detector: BlinkDetector,
    private val startMs: Long,
    private val measureMs: Long = 8_000,
    private val litMs: Long = 2_500,
    private val gapMs: Long = 1_200,
    val needed: Int = 3,
    private val log: EventLog = EventLog.None,
) {
    private val levels = mutableListOf<Float>()
    private val naturalBlinks = mutableListOf<Long>()
    private var measured = false

    private var round = 0
    private var roundStartMs = startMs + measureMs + gapMs

    var caught = 0
        private set
    var missed = 0
        private set
    var falseBlinks = 0
        private set

    val done get() = caught >= needed

    /** Caught blinks out of every chance and every stray blink, or null before the first. */
    val accuracyPercent: Float?
        get() = (caught + missed + falseBlinks).takeIf { it > 0 }?.let { caught * 100f / it }

    fun onSample(sample: EyeSample) {
        val event = detector.onSample(sample)
        val now = sample.timeMs
        if (!measured) {
            measure(event)
            // Measuring only counts time the face was seen, so it waits for the person.
            if (now >= startMs + measureMs && levels.size >= MIN_LEVEL_SAMPLES) finishMeasuring(now)
            return
        }
        if (done || event !is BlinkEvent.Blink) return
        if (starLit(event.startMs) || starLit(now)) {
            caught++
            log.write("practice", "caught ${event.durationMs} ms blink, $caught of $needed")
            nextRound(now)
        } else {
            falseBlinks++
            log.write("practice", "blink ${event.durationMs} ms with no star lit")
        }
    }

    /** Moves the rounds on to [nowMs] and returns what the practice screen shows. */
    fun update(nowMs: Long): PracticeUi {
        if (measured) {
            // No star, and no missed rounds, while the face is not seen.
            if (!detector.tracking && !done) roundStartMs = maxOf(roundStartMs, nowMs + gapMs)
            advanceRounds(nowMs)
        }
        val starAt = if (measured && !done && starLit(nowMs)) round % TARGETS else -1
        return PracticeUi(
            starAt = starAt,
            caught = caught,
            needed = needed,
            progressNote = progressNote(),
            eyeOpen = detector.eyeLevel,
            blinkLevel = detector.closedBelow,
            eyeState = when {
                !detector.tracking -> "Looking for you"
                !measured -> "Measuring"
                detector.eyesShut -> "Shut"
                else -> "Steady"
            },
            holdTimeSeconds = detector.settings.minBlinkMs / 1000f,
            longBlinkSeconds = detector.settings.chooseBlinkMs / 1000f,
        )
    }

    private fun measure(event: BlinkEvent?) {
        if (detector.tracking && !detector.eyesShut) levels += detector.eyeLevel
        when (event) {
            is BlinkEvent.Blink -> naturalBlinks += event.durationMs
            is BlinkEvent.Rejected -> if (event.durationMs < NATURAL_BLINK_LIMIT_MS) naturalBlinks += event.durationMs
            else -> Unit
        }
    }

    private fun finishMeasuring(nowMs: Long) {
        measured = true
        detector.openLevel = levels.sorted()[levels.size / 2]
        naturalBlinks.filter { it < NATURAL_BLINK_LIMIT_MS }.maxOrNull()?.let { longest ->
            val hold = (longest + HOLD_MARGIN_MS).coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)
            val choose = maxOf(detector.settings.chooseBlinkMs, hold + SHORT_TO_LONG_GAP_MS)
            detector.settings = detector.settings.copy(minBlinkMs = hold, chooseBlinkMs = choose)
        }
        log.write(
            "practice",
            "calibrated: open level ${"%.2f".format(Locale.US, detector.openLevel)}, " +
                "natural blinks $naturalBlinks, short blink from ${detector.settings.minBlinkMs} ms, " +
                "long blink from ${detector.settings.chooseBlinkMs} ms",
        )
        roundStartMs = maxOf(roundStartMs, nowMs + gapMs)
    }

    private fun starLit(timeMs: Long) = timeMs in roundStartMs until roundStartMs + litMs

    /** Moves past rounds whose star went out with no blink. */
    private fun advanceRounds(nowMs: Long) {
        while (!done && nowMs >= roundStartMs + litMs) {
            missed++
            round++
            roundStartMs += litMs + gapMs
        }
    }

    private fun nextRound(nowMs: Long) {
        round++
        roundStartMs = nowMs + gapMs
    }

    private fun progressNote(): String {
        if (!measured) return "Keep your eyes open while it measures"
        return when (val left = needed - caught) {
            0 -> "You are ready"
            1 -> "One more and you are ready"
            2 -> "Two more and you are ready"
            else -> "$left more and you are ready"
        }
    }

    private companion object {
        const val TARGETS = 3
        const val MIN_LEVEL_SAMPLES = 10
        const val NATURAL_BLINK_LIMIT_MS = 600L
        const val HOLD_MARGIN_MS = 150L
        const val MIN_HOLD_MS = 400L
        const val MAX_HOLD_MS = 700L
        const val SHORT_TO_LONG_GAP_MS = 400L
    }
}
