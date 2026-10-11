package com.outspoken.tutorial

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.help.HelpStep
import com.outspoken.help.HelpTrigger
import com.outspoken.log.EventLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HelpStage { CloseEyes, OpenAndBlink, Done }

/** [holdProgress] runs from 0 to 1 over the two second hold. */
data class HelpPracticeUi(
    val stage: HelpStage,
    val holdProgress: Float,
    val note: String,
    val faceFound: Boolean,
)

/**
 * The "Calling for help" practice: the same two steps as the real help alarm (hold the eyes shut,
 * then open and blink), read by the same [HelpTrigger], but it only says "well done". No alarm
 * sounds and no SOS contact is reached.
 */
class HelpPractice(
    private val detector: BlinkDetector,
    private val log: EventLog = EventLog.None,
    private val speak: (String) -> Unit = {},
    private val cue: () -> Unit = {},
) {
    // Its lines are marked as practice, so the logs never show a practice as a real call for help.
    private val practiceLog = EventLog { _, message -> log.write("tutorial", "help practice: $message") }
    private var trigger = HelpTrigger(holdMs = HOLD_MS, log = practiceLog)
    private var stage = HelpStage.CloseEyes
    private var note = CLOSE_NOTE
    private var wasArmed = false
    private var clockMs = 0L

    private val _ui = MutableStateFlow(HelpPracticeUi(stage, 0f, note, faceFound = false))
    val ui: StateFlow<HelpPracticeUi> = _ui.asStateFlow()

    fun start(timeMs: Long) {
        val nowMs = advance(timeMs)
        trigger = HelpTrigger(holdMs = HOLD_MS, log = practiceLog)
        stage = HelpStage.CloseEyes
        note = CLOSE_NOTE
        wasArmed = false
        log.write("tutorial", "help practice started")
        speak(note)
        publish(nowMs)
    }

    fun onSample(sample: EyeSample) {
        val nowMs = advance(sample.timeMs)
        val event = detector.onSample(sample)
        if (stage != HelpStage.Done) {
            if (event is BlinkEvent.Blink) trigger.onBlink(event.startMs)?.let(::onStep)
            trigger.onEyes(detector.shutSinceMs, nowMs, detector.settings.maxBlinkMs)?.let(::onStep)
        }
        if (stage == HelpStage.OpenAndBlink) {
            if (trigger.armed) {
                wasArmed = true
            } else if (wasArmed) {
                wasArmed = false
                stage = HelpStage.CloseEyes
                note = "Too slow. After the beep, open your eyes and blink within five seconds. Close your eyes to try again."
                log.write("tutorial", "help practice: no blink in time after the hold")
                speak(note)
            }
        }
        publish(nowMs)
    }

    private fun onStep(step: HelpStep) {
        when (step) {
            HelpStep.HoldReached -> {
                stage = HelpStage.OpenAndBlink
                note = "Now open your eyes, then blink once."
                cue()
                speak(note)
            }
            HelpStep.Alarm -> {
                stage = HelpStage.Done
                note = "Well done. That is how you call for help. In practice no alarm sounds and nobody is contacted."
                log.write("tutorial", "help practice finished, no alarm sounded")
                speak(note)
            }
        }
    }

    private fun advance(timeMs: Long): Long {
        clockMs = maxOf(clockMs, timeMs)
        return clockMs
    }

    private fun publish(nowMs: Long) {
        val shutSince = detector.shutSinceMs
        val progress = when {
            stage != HelpStage.CloseEyes -> 1f
            shutSince == null -> 0f
            else -> ((nowMs - shutSince).toFloat() / HOLD_MS).coerceIn(0f, 1f)
        }
        _ui.value = HelpPracticeUi(stage, progress, note, detector.tracking)
    }

    companion object {
        /** The same hold as the real help alarm. */
        const val HOLD_MS = 2_000L
        private const val CLOSE_NOTE = "Close your eyes and keep them closed until you hear the beep."
    }
}
