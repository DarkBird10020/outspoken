package com.outspoken.help

import com.outspoken.log.EventLog

enum class HelpStep {
    /** The eyes have been shut for the hold time; a cue tells the person to open and confirm. */
    HoldReached,

    /** The confirm blink came in time: sound the alarm. */
    Alarm,
}

/**
 * The help alarm (PRD F8): eyes held shut for [holdMs], then opened, then closed again on purpose
 * within [confirmWithinMs], as one deliberate blink or a longer hold. The second step keeps a long
 * rest with the eyes closed from raising the alarm on its own.
 */
class HelpTrigger(
    private val holdMs: Long = 2_000,
    private val confirmWithinMs: Long = 5_000,
    private val log: EventLog = EventLog.None,
) {
    private var holdReported = false
    private var armedAtMs: Long? = null

    /** The close now going on raised the alarm, so it neither re-arms nor picks a card. */
    private var confirmedByClose = false

    val armed get() = armedAtMs != null

    /** A closure this long is the help hold, and the close that confirmed the alarm: never a pick. */
    fun isHold(durationMs: Long) = durationMs >= holdMs || confirmedByClose

    /**
     * Call with every frame. [shutSinceMs] is when the eyes shut, or null while they are open.
     * [longestBlinkMs] is the longest close that still counts as a blink; a confirming close
     * longer than that raises the alarm here, since no blink will come for it.
     */
    fun onEyes(shutSinceMs: Long?, nowMs: Long, longestBlinkMs: Long = LONGEST_BLINK_MS): HelpStep? {
        armedAtMs?.let { armedAt ->
            val confirming = shutSinceMs != null && shutSinceMs >= armedAt && shutSinceMs - armedAt <= confirmWithinMs
            // On the phone the person closed the eyes again for 2.6 to 2.8 s instead of blinking,
            // three times in a row, and the alarm never sounded (05:08:31 to 05:08:56).
            if (confirming && nowMs - shutSinceMs!! > longestBlinkMs) {
                armedAtMs = null
                holdReported = true
                confirmedByClose = true
                log.write("help", "confirmed by closing the eyes again, alarm")
                return HelpStep.Alarm
            }
            if (!confirming && nowMs - armedAt > confirmWithinMs) {
                log.write("help", "no confirm blink within ${confirmWithinMs / 1000} s, cancelled")
                armedAtMs = null
            }
        }
        if (shutSinceMs != null) {
            if (!holdReported && nowMs - shutSinceMs >= holdMs) {
                holdReported = true
                log.write("help", "eyes held shut ${holdMs / 1000} s, open them and blink to call for help")
                return HelpStep.HoldReached
            }
        } else if (holdReported) {
            holdReported = false
            if (confirmedByClose) confirmedByClose = false else armedAtMs = nowMs
        }
        return null
    }

    /** A deliberate blink. Returns [HelpStep.Alarm] when it confirms, and then the blink picks nothing. */
    fun onBlink(blinkStartMs: Long): HelpStep? {
        val armedAt = armedAtMs ?: return null
        if (blinkStartMs < armedAt) return null
        armedAtMs = null
        log.write("help", "confirmed, alarm")
        return HelpStep.Alarm
    }

    private companion object {
        const val LONGEST_BLINK_MS = 1_500L
    }
}
