package com.outspoken.help

import com.outspoken.log.EventLog

enum class HelpStep {
    /** The eyes have been shut for the hold time; a cue tells the person to open and confirm. */
    HoldReached,

    /** The confirm blink came in time: sound the alarm. */
    Alarm,
}

/**
 * The help alarm (PRD F8): eyes held shut for [holdMs], then opened, then one deliberate blink
 * within [confirmWithinMs]. The second step keeps a long rest with the eyes closed from raising
 * the alarm on its own.
 */
class HelpTrigger(
    private val holdMs: Long = 2_000,
    private val confirmWithinMs: Long = 5_000,
    private val log: EventLog = EventLog.None,
) {
    private var holdReported = false
    private var armedAtMs: Long? = null

    val armed get() = armedAtMs != null

    /** A closure this long is the help hold, never a pick. */
    fun isHold(durationMs: Long) = durationMs >= holdMs

    /** Call with every frame. [shutSinceMs] is when the eyes shut, or null while they are open. */
    fun onEyes(shutSinceMs: Long?, nowMs: Long): HelpStep? {
        armedAtMs?.let {
            if (nowMs - it > confirmWithinMs) {
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
            armedAtMs = nowMs
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
}
