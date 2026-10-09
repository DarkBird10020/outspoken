package com.outspoken.blink

import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog

/** Which eye winked, by the person's own left and right. */
enum class Wink { Left, Right }

/**
 * One eye shut while the other stays open, held for [holdMs]: the owner's second way to move the
 * highlight (left wink = down, right wink = up). Fires once per wink, while the eye is still shut.
 * The closed eye must be under the shut line and the open eye above the open line, at least
 * [MIN_DIFFERENCE] apart, so a blink that shuts one eye a frame before the other is never a wink,
 * and looking down (both eyes lower together) is not either.
 *
 * The lid gap check is not used here: the face tracker never draws one eye fully shut while the
 * other is wide open, so a winking eye's gap stayed at about 0.18 against a calibrated shut line
 * of 0.10 (phone run 01:56:48) and no wink registered.
 */
class WinkDetector(
    var holdMs: Long = 300,
    private val log: EventLog = EventLog.None,
) {
    private var winking: Wink? = null
    private var sinceMs = 0L
    private var fired = false

    /** One eye is held shut right now, whether or not the wink has fired yet. */
    val active: Boolean get() = winking != null

    fun onSample(sample: EyeSample, settings: BlinkSettings): Wink? {
        val left = sample.leftOpen
        val right = sample.rightOpen
        if (!sample.faceFound || left == null || right == null) {
            winking = null
            return null
        }
        fun winks(shut: Float, other: Float) =
            shut < settings.closedBelow && other > settings.openAbove && other - shut >= MIN_DIFFERENCE
        val current = when {
            winks(left, right) -> Wink.Left
            winks(right, left) -> Wink.Right
            else -> null
        }
        if (current != winking) {
            winking = current
            sinceMs = sample.timeMs
            fired = false
            return null
        }
        if (current == null || fired || sample.timeMs - sinceMs < holdMs) return null
        fired = true
        log.write("blink", "${current.name.lowercase()} wink ${sample.timeMs - sinceMs} ms")
        return current
    }

    private companion object {
        /** How much more open the open eye must read than the winking one. */
        const val MIN_DIFFERENCE = 0.35f
    }
}
