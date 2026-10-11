package com.outspoken.control

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.help.HelpStep
import com.outspoken.help.HelpTrigger
import com.outspoken.log.EventLog
import com.outspoken.scan.GazeStep
import com.outspoken.scan.GazeStepper

/** What the eyes asked of phone control on one frame. */
sealed interface ControlInput {
    data object Next : ControlInput
    data object Previous : ControlInput
    data object Select : ControlInput
    data class Help(val step: HelpStep) : ControlInput
}

/**
 * The eyes while phone control is on: looks move the lit button, a close presses it, as on the
 * reply cards. The help hold works here too, and its closes press nothing. Its own detector and
 * stepper, so the main page's are left as they were.
 */
class ControlEyes(
    private val detector: BlinkDetector,
    private val gaze: GazeStepper,
    private val help: HelpTrigger,
    private val log: EventLog = EventLog.None,
) {
    /** With looking down off, a look up moves to the next button, as on the main page. */
    var upMovesNext = false

    /** Off ("Blink only"): the lit button moves on by itself every [scanMs]. */
    var moveByEyes = true
    var scanMs = 1_200L

    var helpConfirm: Boolean
        get() = help.confirm
        set(value) {
            help.confirm = value
        }

    private var lastLookMs = Long.MIN_VALUE / 2
    private var lastLookDown = false
    private var lastAutoMs = Long.MIN_VALUE / 2
    private var wasShut = false

    fun onSample(sample: EyeSample): List<ControlInput> {
        val out = mutableListOf<ControlInput>()
        val nowMs = sample.timeMs
        val event = detector.onSample(sample)
        val shutSince = detector.shutSinceMs
        // Lids dropping to close read as a look down; that look is taken back (as on the main page).
        if (shutSince != null && !wasShut && lastLookDown && shutSince - lastLookMs in 0..CLOSE_UNDO_MS) {
            log.write("control", "look down undone: the eyes shut ${shutSince - lastLookMs} ms after it")
            out += ControlInput.Previous
            lastLookDown = false
        }
        wasShut = shutSince != null
        if (event is BlinkEvent.Blink) {
            val step = help.onBlink(event.startMs)
            when {
                step != null -> out += ControlInput.Help(step)
                !help.isHold(event.durationMs) -> out += ControlInput.Select
            }
        }
        help.onEyes(shutSince, nowMs, faceSeen = detector.tracking)?.let { out += ControlInput.Help(it) }
        if (moveByEyes) {
            gaze.onSample(sample.gaze, !detector.eitherEyeShut(sample), nowMs, sample.irisY)?.let { step ->
                lastLookMs = nowMs
                lastLookDown = step == GazeStep.Next
                out += if (step == GazeStep.Next || upMovesNext) ControlInput.Next else ControlInput.Previous
            }
        } else if (shutSince == null && detector.tracking && nowMs - lastAutoMs >= scanMs) {
            lastAutoMs = nowMs
            out += ControlInput.Next
        }
        return out
    }

    /** Starts afresh, so a close begun before control started is not taken. */
    fun reset() {
        detector.reset()
        wasShut = false
        lastLookDown = false
    }

    private companion object {
        /** As on the main page: nobody sees a new button and decides in under about 200 ms. */
        const val CLOSE_UNDO_MS = 200L
    }
}
