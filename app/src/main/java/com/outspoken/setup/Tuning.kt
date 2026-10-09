package com.outspoken.setup

import android.content.Context
import com.outspoken.blink.BlinkSettings
import com.outspoken.scan.GazeSettings

/** Blink and scan settings the person at the bedside can adjust on the eye check screen. */
data class Tuning(
    val blink: BlinkSettings = BlinkSettings(
        // From the iQOO logs: deliberate closes reached 0.22 to 0.36 and lasted 0.31 to 0.65 s;
        // normal blinks bottomed at 0.40 to 0.53 and lasted up to 0.25 s. Looking down at the
        // phone alone drops the value to about 0.6, so the open line sits below that.
        closedBelow = 0.45f,
        openAbove = 0.55f,
        // Phone logs 2026-10-10 (02:37, 02:28, 03:11, 03:23 runs): unprompted closes lasted 74 to
        // 242 ms, and at 200 ms every one from 200 to 242 ms picked a card ("I need water" five
        // times in 11 s). Quick deliberate closes are the same length, so a pick needs a longer
        // hold.
        minBlinkMs = 400,
        maxBlinkMs = 1_500,
        // A pick waits for the eyes to open. Choosing while shut said the lit card 0.4 s into
        // every help hold (PRD F8: eyes shut 2 s, then a confirm blink), before the alarm.
        chooseWhileShut = false,
        // Two unprompted blinks in a row picked "Good evening, how are you?" (03:21:10 run).
        doubleBlink = false,
        smoothing = 0.65f,
        // MediaPipe reads eyes further round than ML Kit's 18°.
        maxHeadTurnDeg = 30f,
    ),
    val scanMs: Long = 1_200,
    /** The highlight moves when the eyes look up. Off: it moves on its own every [scanMs]. */
    val moveByEyes: Boolean = true,
    /** Left wink moves down, right wink up. Off by default, owner decision. */
    val winks: Boolean = false,
    // Iris look down on from the start (calibrated runs on the phone gave 0.008 to 0.011), so a
    // failed calibration or a fresh install never leaves looking down off.
    val gaze: GazeSettings = GazeSettings(lookStrength = 0.3f, irisDownStrength = 0.012f),
    /**
     * Looking down also moves the highlight. Off by default: it failed about eight phone runs in a
     * row (it fired on its own and missed real looks). Off, a look up moves to the next card.
     */
    val lookDown: Boolean = false,
) {
    /** The gaze settings the stepper uses: the look down lines only when looking down is on. */
    val activeGaze: GazeSettings
        get() = if (lookDown) gaze else gaze.copy(downStrength = null, irisDownStrength = null)
}

/** Longest shortest-blink the practice round may set. */
const val MAX_PRACTICE_BLINK_MS = 400L

/** No pick hold below this: unprompted blinks on the phone reached 242 ms. */
const val MIN_PICK_HOLD_MS = 350L

/** Keeps [Tuning] across app restarts. */
class TuningStore(context: Context) {

    private val prefs = context.getSharedPreferences("tuning", Context.MODE_PRIVATE)

    fun load(): Tuning {
        val default = Tuning()
        val blink = default.blink
        val rawMinBlink = prefs.getLong("minBlinkMs", blink.minBlinkMs)
        val rawLookStrength = prefs.getFloat("lookStrength", default.gaze.lookStrength)
        // Guard against stale early settings saved on device that caused hyper-sensitive triggers
        // Builds before version 2 let the practice round save up to 600 ms, which then threw away
        // deliberate closes (phone log 01:30:06 "blink ignored 595 ms, shorter than 600 ms").
        val oldPractice = prefs.getInt("version", 1) < VERSION && rawMinBlink > MAX_PRACTICE_BLINK_MS
        val safeMinBlink = if (rawMinBlink < MIN_PICK_HOLD_MS || oldPractice) blink.minBlinkMs else rawMinBlink
        val safeLookStrength = if (rawLookStrength < 0.35f) default.gaze.lookStrength else rawLookStrength
        return Tuning(
            blink = blink.copy(
                closedBelow = prefs.getFloat("closedBelow", blink.closedBelow),
                openAbove = prefs.getFloat("openAbove", blink.openAbove),
                minBlinkMs = safeMinBlink,
                maxBlinkMs = prefs.getLong("maxBlinkMs", blink.maxBlinkMs),
                shapeClosedBelow = prefs.getFloat("shapeClosedBelow", NOT_SET).takeIf { it != NOT_SET },
                shapeOpenAbove = prefs.getFloat("shapeOpenAbove", NOT_SET).takeIf { it != NOT_SET },
            ),
            scanMs = prefs.getLong("scanMs", default.scanMs),
            moveByEyes = prefs.getBoolean("moveByEyes", default.moveByEyes),
            lookDown = prefs.getBoolean("lookDown", default.lookDown),
            winks = prefs.getBoolean("winks", default.winks),
            gaze = GazeSettings(
                lookStrength = safeLookStrength,
                irisDownStrength = if (prefs.contains("irisDownStrength")) {
                    prefs.getFloat("irisDownStrength", NOT_SET).takeIf { it != NOT_SET }
                } else {
                    default.gaze.irisDownStrength
                },
                downStrength = if (prefs.contains("downStrength")) {
                    prefs.getFloat("downStrength", NOT_SET).takeIf { it != NOT_SET }
                } else {
                    default.gaze.downStrength
                },
                lookHoldMs = prefs.getLong("lookHoldMs", default.gaze.lookHoldMs),
            ),
        )
    }

    fun hasCompletedPractice(): Boolean = prefs.getBoolean("practiceCompleted", false)

    fun markPracticeCompleted() {
        prefs.edit().putBoolean("practiceCompleted", true).apply()
    }

    fun save(tuning: Tuning) {
        prefs.edit()
            .putInt("version", VERSION)
            .putFloat("closedBelow", tuning.blink.closedBelow)
            .putFloat("openAbove", tuning.blink.openAbove)
            .putLong("minBlinkMs", tuning.blink.minBlinkMs)
            .putLong("maxBlinkMs", tuning.blink.maxBlinkMs)
            .putFloat("shapeClosedBelow", tuning.blink.shapeClosedBelow ?: NOT_SET)
            .putFloat("shapeOpenAbove", tuning.blink.shapeOpenAbove ?: NOT_SET)
            .putLong("scanMs", tuning.scanMs)
            .putBoolean("moveByEyes", tuning.moveByEyes)
            .putBoolean("lookDown", tuning.lookDown)
            .putBoolean("winks", tuning.winks)
            .putFloat("lookStrength", tuning.gaze.lookStrength)
            .putFloat("downStrength", tuning.gaze.downStrength ?: NOT_SET)
            .putFloat("irisDownStrength", tuning.gaze.irisDownStrength ?: NOT_SET)
            .putLong("lookHoldMs", tuning.gaze.lookHoldMs)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val NOT_SET = -1f
        const val VERSION = 2
    }
}
