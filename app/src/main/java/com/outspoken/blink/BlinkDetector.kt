package com.outspoken.blink

import com.outspoken.eye.EyeSample
import kotlin.math.abs

/**
 * Eye-open values below [closedBelow] count as shut, above [openAbove] as open; in between keeps
 * the current state so noise near one line does not flicker. Calibration (M3) will set these per
 * person.
 */
data class BlinkSettings(
    val closedBelow: Float = 0.3f,
    val openAbove: Float = 0.5f,
    val minBlinkMs: Long = 300,
    val maxBlinkMs: Long = 900,
    val maxHeadTurnDeg: Float = 25f,
)

sealed interface BlinkEvent {
    /** An intentional blink. [startMs] is when the eyes shut. */
    data class Blink(val startMs: Long, val durationMs: Long) : BlinkEvent
    data object FaceLost : BlinkEvent
    data object FaceFound : BlinkEvent
}

/**
 * Turns eye samples into blink events. Both eyes must shut together, so a wink does not count.
 * Normal fast blinks and long closures fall outside the blink window and are ignored.
 */
class BlinkDetector(var settings: BlinkSettings = BlinkSettings()) {

    var tracking = false
        private set

    private var closedSinceMs: Long? = null

    fun onSample(sample: EyeSample): BlinkEvent? {
        val left = sample.leftOpen
        val right = sample.rightOpen
        if (!sample.faceFound || left == null || right == null || !facingCamera(sample)) {
            closedSinceMs = null
            if (!tracking) return null
            tracking = false
            return BlinkEvent.FaceLost
        }

        val wasTracking = tracking
        tracking = true
        val closedSince = closedSinceMs
        if (closedSince == null) {
            if (left < settings.closedBelow && right < settings.closedBelow) closedSinceMs = sample.timeMs
        } else if (maxOf(left, right) > settings.openAbove) {
            closedSinceMs = null
            val duration = sample.timeMs - closedSince
            if (duration in settings.minBlinkMs..settings.maxBlinkMs) {
                return BlinkEvent.Blink(closedSince, duration)
            }
        }
        return if (wasTracking) null else BlinkEvent.FaceFound
    }

    private fun facingCamera(sample: EyeSample) =
        abs(sample.yawDeg) <= settings.maxHeadTurnDeg && abs(sample.pitchDeg) <= settings.maxHeadTurnDeg
}
