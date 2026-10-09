package com.outspoken.practice

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog
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

        when (val event = detector.onSample(sample)) {
            is BlinkEvent.Blink -> onBlink(event)
            else -> Unit
        }

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
            val calibratedHoldTime = (blink.durationMs * 0.85f).toLong().coerceIn(350L, 600L)

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

    fun reset() {
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
}
