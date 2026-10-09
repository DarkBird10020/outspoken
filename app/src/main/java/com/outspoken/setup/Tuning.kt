package com.outspoken.setup

import android.content.Context
import com.outspoken.blink.BlinkSettings
import com.outspoken.scan.GazeSettings

/** Blink and scan settings the person at the bedside can adjust on the eye check screen. */
data class Tuning(
    val blink: BlinkSettings = BlinkSettings(
        // MediaPipe's blink score rarely reaches 1 with the eyes shut, so "open" here
        // (1 - blink score) bottoms out well above 0. Start points; the sliders adjust them.
        closedBelow = 0.55f,
        openAbove = 0.7f,
        minBlinkMs = 250,
        maxBlinkMs = 900,
        // MediaPipe reads eyes further round than ML Kit's 18°.
        maxHeadTurnDeg = 30f,
    ),
    val scanMs: Long = 1_200,
    /** The highlight moves when the eyes look down or up. Off: it moves on its own every [scanMs]. */
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
            .putLong("scanMs", tuning.scanMs)
            .putBoolean("moveByEyes", tuning.moveByEyes)
            .putFloat("lookStrength", tuning.gaze.lookStrength)
            .putLong("lookHoldMs", tuning.gaze.lookHoldMs)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
