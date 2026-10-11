package com.outspoken.help

import com.outspoken.log.EventLog

enum class HelpStep {
    /** The eyes have been shut for the hold time; a cue tells the person to open and confirm. */
    HoldReached,

    /** The confirm blink came in time: sound the alarm. */
    Alarm,
}

/**
 * The help alarm (PRD F8): eyes held shut for [holdMs], then opened, then a confirm within
 * [confirmWithinMs]: one deliberate close that opens again, or two quick blinks in a row. Built so
 * sleep does not call for help (owner: "like someone is sleeping, it will trigger on its own"):
 * - eyes shut longer than [restMs] are rest or sleep, not a hold, and arm nothing;
 * - a confirming close that stays shut past [confirmMaxMs] is dozing off again, not a confirm;
 * - a long close that starts within [minOpenMs] of the eyes opening is a flicker, not a confirm;
 * - quick blinks count only in a pair no more than [twoBlinksWithinMs] apart, each shut at least
 *   [minQuickBlinkMs] after at least [minQuickOpenMs] open; blinking now and then is not a pair;
 * - the face going out of view cancels it all; before, that read as the eyes opening.
 * Deliberate calls on the phone held 2.6 to 7.9 s, opened 0.3 s or more before the confirm, and
 * confirmed with closes of 0.4 to 2 s (18:26 to 06:58 runs). Later the owner found it hard
 * (logs-60_1): after the hold they blinked quickly two to four times, closes of 95 to 330 ms with
 * 70 to 200 ms open between, and none counted. Two calls failed (07:37:09, 07:44:29) and two took
 * 8 and 6 s (07:37:30, 07:44:49). Such a pair now confirms. A 567 ms close 142 ms after opening
 * (07:37:53) was taken for a flicker at 250 ms; a flicker is now under [minOpenMs], 120 ms.
 *
 * With [confirm] off, the hold alone raises the alarm (a Settings switch).
 */
class HelpTrigger(
    private val holdMs: Long = 2_000,
    private val confirmWithinMs: Long = 5_000,
    private val log: EventLog = EventLog.None,
    private val restMs: Long = 10_000,
    private val confirmMaxMs: Long = 5_000,
    private val minOpenMs: Long = 120,
    private val minConfirmMs: Long = 400,
    private val minQuickBlinkMs: Long = 80,
    private val minQuickOpenMs: Long = 60,
    private val twoBlinksWithinMs: Long = 700,
) {
    var confirm = true

    private enum class Stage { Idle, Holding, Resting, Armed, Confirming }

    private var stage = Stage.Idle

    /** The start of a close already used, so it does not start a hold or a confirm again. */
    private var usedClose: Long? = null
    private var armedAtMs = 0L
    private var openSinceMs = 0L
    private var confirmStartMs = 0L
    private var openBeforeConfirmMs = 0L

    /** When the last quick blink after the hold ended, waiting for a second one. */
    private var quickBlinkEndMs: Long? = null
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
                    quickBlinkEndMs = null
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
                else -> {
                    // How long it lasts decides what it is: a long close or a quick blink.
                    usedClose = shutSinceMs
                    confirmStartMs = shutSinceMs
                    openBeforeConfirmMs = shutSinceMs - openSinceMs
                    stage = Stage.Confirming
                }
            }
            Stage.Confirming -> when {
                shutSinceMs != null && nowMs - shutSinceMs > confirmMaxMs -> {
                    stage = Stage.Resting
                    log.write("help", "eyes shut again over ${confirmMaxMs / 1000} s: back to rest, no alarm")
                }
                shutSinceMs != null -> Unit
                else -> return closeEnded(nowMs)
            }
        }
        return null
    }

    /**
     * A deliberate blink, given before [onEyes] for the same frame. Returns [HelpStep.Alarm] when
     * it is the confirm, and then the blink picks nothing.
     */
    fun onBlink(blinkStartMs: Long): HelpStep? {
        if (stage != Stage.Confirming || blinkStartMs != confirmStartMs || openBeforeConfirmMs < minOpenMs) return null
        stage = Stage.Idle
        log.write("help", "confirmed, alarm")
        return HelpStep.Alarm
    }

    /** The eyes opened after a close begun while armed. */
    private fun closeEnded(nowMs: Long): HelpStep? {
        val shutFor = nowMs - confirmStartMs
        stage = Stage.Armed
        openSinceMs = nowMs
        when {
            shutFor >= minConfirmMs && openBeforeConfirmMs < minOpenMs -> {
                // Eyes open for a moment only: a flicker, which sleep can give, not a confirm.
                log.write("help", "eyes open only $openBeforeConfirmMs ms before closing again: not a confirm")
            }
            shutFor >= minConfirmMs -> {
                stage = Stage.Idle
                log.write("help", "confirmed by closing the eyes again, alarm")
                return HelpStep.Alarm
            }
            shutFor >= minQuickBlinkMs && openBeforeConfirmMs >= minQuickOpenMs -> {
                val last = quickBlinkEndMs
                if (last != null && confirmStartMs - last <= twoBlinksWithinMs) {
                    stage = Stage.Idle
                    log.write("help", "confirmed with two quick blinks ${confirmStartMs - last} ms apart, alarm")
                    return HelpStep.Alarm
                }
                quickBlinkEndMs = nowMs
            }
        }
        return null
    }
}
