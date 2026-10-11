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
 * within [confirmWithinMs], as one deliberate blink or a longer close that opens again. Built so
 * sleep does not call for help (owner: "like someone is sleeping, it will trigger on its own"):
 * - eyes shut longer than [restMs] are rest or sleep, not a hold, and arm nothing;
 * - a confirming close that stays shut past [confirmMaxMs] is dozing off again, not a confirm;
 * - a close that starts within [minOpenMs] of the eyes opening is a flicker, not a confirm;
 * - the face going out of view cancels it all; before, that read as the eyes opening.
 * Deliberate calls on the phone held 2.6 to 7.9 s, opened 0.3 s or more before the confirm, and
 * confirmed with closes of 0.4 to 2 s (18:26 to 06:58 runs).
 *
 * With [confirm] off, the hold alone raises the alarm (a Settings switch).
 */
class HelpTrigger(
    private val holdMs: Long = 2_000,
    private val confirmWithinMs: Long = 5_000,
    private val log: EventLog = EventLog.None,
    private val restMs: Long = 10_000,
    private val confirmMaxMs: Long = 5_000,
    private val minOpenMs: Long = 250,
    private val minConfirmMs: Long = 400,
) {
    var confirm = true

    private enum class Stage { Idle, Holding, Resting, Armed, Confirming }

    private var stage = Stage.Idle

    /** The start of a close already used, so it does not start a hold or a confirm again. */
    private var usedClose: Long? = null
    private var armedAtMs = 0L
    private var openSinceMs = 0L
    private var confirmStartMs = 0L
    private var wasShut = false

    val armed get() = stage == Stage.Armed || stage == Stage.Confirming

    /** A closure this long is the help hold: never a pick. */
    fun isHold(durationMs: Long) = durationMs >= holdMs

    /**
     * Call with every frame. [shutSinceMs] is when the eyes shut, or null while they are open.
     * [faceSeen] is false while the face is out of view; then nothing is known about the eyes.
     */
    fun onEyes(shutSinceMs: Long?, nowMs: Long, faceSeen: Boolean = true): HelpStep? {
        val opened = shutSinceMs == null && wasShut
        wasShut = shutSinceMs != null
        if (!faceSeen) {
            if (stage != Stage.Idle && stage != Stage.Resting) log.write("help", "face out of view, help call cancelled")
            stage = Stage.Idle
            return null
        }
        when (stage) {
            Stage.Idle -> if (shutSinceMs != null && shutSinceMs != usedClose && nowMs - shutSinceMs >= holdMs) {
                usedClose = shutSinceMs
                if (!confirm) {
                    log.write("help", "eyes held shut ${holdMs / 1000} s, alarm")
                    return HelpStep.Alarm
                }
                stage = Stage.Holding
                log.write("help", "eyes held shut ${holdMs / 1000} s, open them and blink to call for help")
                return HelpStep.HoldReached
            }
            Stage.Holding -> when {
                shutSinceMs == null -> {
                    stage = Stage.Armed
                    armedAtMs = nowMs
                    openSinceMs = nowMs
                }
                nowMs - shutSinceMs > restMs -> {
                    stage = Stage.Resting
                    log.write("help", "eyes shut over ${restMs / 1000} s: rest or sleep, not a help call")
                }
            }
            Stage.Resting -> if (shutSinceMs == null) stage = Stage.Idle
            Stage.Armed -> when {
                nowMs - armedAtMs > confirmWithinMs -> {
                    log.write("help", "no confirm blink within ${confirmWithinMs / 1000} s, cancelled")
                    stage = Stage.Idle
                }
                shutSinceMs == null -> if (opened) openSinceMs = nowMs
                shutSinceMs == usedClose -> Unit
                shutSinceMs - openSinceMs < minOpenMs -> {
                    // Eyes open for a moment only: a flicker, which sleep can give, not a confirm.
                    usedClose = shutSinceMs
                    log.write("help", "eyes open only ${shutSinceMs - openSinceMs} ms before closing again: not a confirm")
                }
                else -> {
                    usedClose = shutSinceMs
                    confirmStartMs = shutSinceMs
                    stage = Stage.Confirming
                }
            }
            Stage.Confirming -> when {
                shutSinceMs != null && nowMs - shutSinceMs > confirmMaxMs -> {
                    stage = Stage.Resting
                    log.write("help", "eyes shut again over ${confirmMaxMs / 1000} s: back to rest, no alarm")
                }
                shutSinceMs != null -> Unit
                nowMs - confirmStartMs >= minConfirmMs -> {
                    stage = Stage.Idle
                    log.write("help", "confirmed by closing the eyes again, alarm")
                    return HelpStep.Alarm
                }
                else -> {
                    // A normal blink: still waiting for the confirm.
                    stage = Stage.Armed
                    openSinceMs = nowMs
                }
            }
        }
        return null
    }

    /**
     * A deliberate blink, given before [onEyes] for the same frame. Returns [HelpStep.Alarm] when
     * it is the confirm, and then the blink picks nothing.
     */
    fun onBlink(blinkStartMs: Long): HelpStep? {
        if (stage != Stage.Confirming || blinkStartMs != confirmStartMs) return null
        stage = Stage.Idle
        log.write("help", "confirmed, alarm")
        return HelpStep.Alarm
    }
}
