package com.outspoken.practice

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog
import com.outspoken.setup.MAX_PRACTICE_BLINK_MS
import com.outspoken.setup.Tuning
import com.outspoken.ui.PracticeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Controller for the practice / tutorial round (PRD F6).
 * Guides the user through catching stars with deliberate blinks and
 * auto-calibrates eye-open thresholds and hold time.
 */
class PracticeController(
    initialTuning: Tuning = Tuning(),
    private val detector: BlinkDetector = BlinkDetector(settings = initialTuning.blink),
    private val log: EventLog = EventLog.None,
    private val speak: ((String) -> Unit)? = null,
    val onCalibrated: ((Tuning) -> Unit)? = null,
) {
    var tuning: Tuning = initialTuning
        private set

    private var starAt = 0
    private var caught = 0
    private val needed = 3

    private var lowestDuringClosure = 1f
    private var baselineOpen = 0.8f
    private var lastEyeOpen = 0.8f
    private var lastEyeState = "Looking for you"

    // Blink accuracy (PRD S1): of the closes long enough to be a try, how many caught a star.
    private var closeStartMs: Long? = null
    private var closeCaught = false
    private var missed = 0

    /** Share of tries that caught a star, 0 to 100; null before the first try. */
    val accuracyPercent: Float?
        get() = (caught + missed).takeIf { it > 0 }?.let { caught * 100f / it }

    private val _ui = MutableStateFlow(
        PracticeUi(
            starAt = 0,
            caught = 0,
            needed = needed,
            progressNote = "Blink when the star lights up",
            eyeOpen = 0.8f,
            blinkLevel = initialTuning.blink.closedBelow,
            eyeState = "Looking for you",
            holdTimeSeconds = initialTuning.blink.minBlinkMs / 1000f,
            scanSpeedSeconds = initialTuning.scanMs / 1000f,
        )
    )
    val ui: StateFlow<PracticeUi> = _ui.asStateFlow()

    fun onSample(sample: EyeSample) {
        val left = sample.leftOpen
        val right = sample.rightOpen
        val avg = if (left != null && right != null) (left + right) / 2f else 0f
        lastEyeOpen = avg

        if (sample.faceFound && left != null && right != null) {
            if (avg > tuning.blink.closedBelow) {
                baselineOpen = baselineOpen * 0.9f + avg * 0.1f
            } else {
                lowestDuringClosure = minOf(lowestDuringClosure, avg)
            }
        }

        lastEyeState = when {
            !sample.faceFound -> "Looking for you"
            kotlin.math.abs(sample.yawDeg) > detector.settings.maxHeadTurnDeg -> "Turn to camera"
            avg < tuning.blink.closedBelow -> "Eyes shut"
            else -> "Steady"
        }

        val event = detector.onSample(sample)
        if (event is BlinkEvent.Blink) {
            closeCaught = true
            onBlink(event)
        }
        countTry(sample.timeMs)

        publish()
    }

    private fun onBlink(blink: BlinkEvent.Blink) {
        if (caught < needed) {
            caught++
            starAt = (starAt + 1) % needed
            log.write("practice", "blink caught: $caught of $needed, ${blink.durationMs} ms")

            // Auto-calibrate thresholds based on observed values
            val shutVal = lowestDuringClosure.coerceIn(0.15f, 0.52f)
            val openVal = baselineOpen.coerceIn(0.60f, 0.95f)
            val newClosedBelow = (shutVal * 0.55f + openVal * 0.45f).coerceIn(0.38f, 0.52f)
            val newOpenAbove = (newClosedBelow + 0.12f).coerceIn(0.50f, 0.65f)
            // Half the close, at most 400 ms: 85% up to 600 ms left the shortest blink at 600 ms
            // on the phone, and then deliberate closes were thrown away ("ignored 595 ms").
            val calibratedHoldTime = (blink.durationMs * HOLD_SHARE).toLong()
                .coerceIn(Tuning().blink.minBlinkMs, MAX_PRACTICE_BLINK_MS)

            tuning = tuning.copy(
                blink = tuning.blink.copy(
                    closedBelow = newClosedBelow,
                    openAbove = newOpenAbove,
                    minBlinkMs = calibratedHoldTime,
                )
            )
            detector.settings = tuning.blink
            onCalibrated?.invoke(tuning)

            // Reset closure tracker for next star
            lowestDuringClosure = 1f

            val speech = when (caught) {
                1 -> "First star caught. Blink again."
                2 -> "Second star caught. One more."
                3 -> "Practice complete. You are ready to talk."
                else -> null
            }
            speech?.let { speak?.invoke(it) }
        }
    }

    /** A close that ends without catching a star is a missed try, unless it was a normal quick blink. */
    private fun countTry(timeMs: Long) {
        val shutSince = detector.shutSinceMs
        if (shutSince != null) {
            if (closeStartMs == null) {
                closeStartMs = shutSince
                closeCaught = false
            }
            return
        }
        val start = closeStartMs ?: return
        closeStartMs = null
        if (!closeCaught && caught < needed && timeMs - start >= MIN_TRY_MS) {
            missed++
            log.write("practice", "missed try, eyes shut ${timeMs - start} ms")
        }
    }

    fun reset() {
        missed = 0
        closeStartMs = null
        caught = 0
        starAt = 0
        lowestDuringClosure = 1f
        publish()
    }

    private fun publish() {
        val note = when (caught) {
            0 -> "Blink when the star lights up"
            1 -> "Star caught! 2 more to calibrate"
            2 -> "One more and you are ready"
            else -> "Ready! Tap start talking below"
        }
        _ui.value = PracticeUi(
            starAt = starAt,
            caught = caught,
            needed = needed,
            progressNote = note,
            eyeOpen = lastEyeOpen,
            blinkLevel = tuning.blink.closedBelow,
            eyeState = lastEyeState,
            holdTimeSeconds = tuning.blink.minBlinkMs / 1000f,
            scanSpeedSeconds = tuning.scanMs / 1000f,
        )
    }

    private companion object {
        const val HOLD_SHARE = 0.5f

        /** Normal blinks last about 100 to 150 ms; shorter closes are not counted as tries. */
        const val MIN_TRY_MS = 150L
    }
}
