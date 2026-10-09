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
        minBlinkMs = 400,
        maxBlinkMs = 1_500,
        // MediaPipe reads eyes further round than ML Kit's 18°.
        maxHeadTurnDeg = 30f,
    ),
    val scanMs: Long = 1_200,
    /** The highlight moves when the eyes look up. Off: it moves on its own every [scanMs]. */
    val moveByEyes: Boolean = true,
    val gaze: GazeSettings = GazeSettings(),
)

/** Keeps [Tuning] across app restarts. */
class TuningStore(context: Context) {

    private val prefs = context.getSharedPreferences("tuning", Context.MODE_PRIVATE)

    fun load(): Tuning {
        val default = Tuning()
        val blink = default.blink
        return Tuning(
            blink = blink.copy(
                closedBelow = prefs.getFloat("closedBelow", blink.closedBelow),
                openAbove = prefs.getFloat("openAbove", blink.openAbove),
                minBlinkMs = prefs.getLong("minBlinkMs", blink.minBlinkMs),
                maxBlinkMs = prefs.getLong("maxBlinkMs", blink.maxBlinkMs),
                shapeClosedBelow = prefs.getFloat("shapeClosedBelow", NOT_SET).takeIf { it != NOT_SET },
                shapeOpenAbove = prefs.getFloat("shapeOpenAbove", NOT_SET).takeIf { it != NOT_SET },
            ),
            scanMs = prefs.getLong("scanMs", default.scanMs),
            moveByEyes = prefs.getBoolean("moveByEyes", default.moveByEyes),
            gaze = GazeSettings(
                lookStrength = prefs.getFloat("lookStrength", default.gaze.lookStrength),
                lookHoldMs = prefs.getLong("lookHoldMs", default.gaze.lookHoldMs),
            ),
        )
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
