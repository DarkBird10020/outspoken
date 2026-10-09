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
        // Spontaneous blinks last about 100 to 150 ms (published blink studies); 0.3 s plus the
        // lid gap check keeps them out and is quicker than the 0.4 s used before.
        // Owner's deliberate quick closes on the phone lasted 160 to 240 ms (01:35 run) and were
        // all ignored at 300 ms. Normal blinks are about 100 to 150 ms.
        minBlinkMs = 200,
        maxBlinkMs = 1_500,
        chooseWhileShut = true,
        doubleBlink = true,
        smoothing = 0.65f,
        // MediaPipe reads eyes further round than ML Kit's 18°.
        maxHeadTurnDeg = 30f,
    ),
    val scanMs: Long = 1_200,
    /** The highlight moves when the eyes look up. Off: it moves on its own every [scanMs]. */
    val moveByEyes: Boolean = true,
    // Iris look down on from the start (calibrated runs on the phone gave 0.008 to 0.011), so a
    // failed calibration or a fresh install never leaves looking down off.
    val gaze: GazeSettings = GazeSettings(lookStrength = 0.3f, irisDownStrength = 0.012f),
)

/** Keeps [Tuning] across app restarts. */
class TuningStore(context: Context) {

    private val prefs = context.getSharedPreferences("tuning", Context.MODE_PRIVATE)

    fun load(): Tuning {
        val default = Tuning()
        val blink = default.blink
        val rawMinBlink = prefs.getLong("minBlinkMs", blink.minBlinkMs)
        val rawLookStrength = prefs.getFloat("lookStrength", default.gaze.lookStrength)
        // Guard against stale early settings saved on device that caused hyper-sensitive triggers
        val safeMinBlink = if (rawMinBlink < 250L) blink.minBlinkMs else rawMinBlink
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
            .putFloat("closedBelow", tuning.blink.closedBelow)
            .putFloat("openAbove", tuning.blink.openAbove)
            .putLong("minBlinkMs", tuning.blink.minBlinkMs)
            .putLong("maxBlinkMs", tuning.blink.maxBlinkMs)
            .putFloat("shapeClosedBelow", tuning.blink.shapeClosedBelow ?: NOT_SET)
            .putFloat("shapeOpenAbove", tuning.blink.shapeOpenAbove ?: NOT_SET)
            .putLong("scanMs", tuning.scanMs)
            .putBoolean("moveByEyes", tuning.moveByEyes)
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
    }
}
