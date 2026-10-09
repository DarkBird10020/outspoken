package com.outspoken.blink

import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog

/** Which eye winked, by the person's own left and right. */
enum class Wink { Left, Right }

/**
 * One eye shut while the other stays open, held for [holdMs]: the owner's second way to move the
 * highlight (left wink = down, right wink = up). Fires once per wink, while the eye is still shut.
 * Uses the same shut lines as [BlinkDetector] for the closed eye; the open eye must be above the
 * open line, so a blink that shuts one eye a frame before the other is never a wink.
 */
class WinkDetector(
    var holdMs: Long = 300,
    private val log: EventLog = EventLog.None,
) {
    private var winking: Wink? = null
    private var sinceMs = 0L
    private var fired = false

    fun onSample(sample: EyeSample, settings: BlinkSettings): Wink? {
        val left = sample.leftOpen
        val right = sample.rightOpen
        if (!sample.faceFound || left == null || right == null) {
            winking = null
            return null
        }
        val dots = sample.dots
        val gapShut = settings.shapeClosedBelow
        fun shut(open: Float, gap: Float?) =
            open < settings.closedBelow && (gapShut == null || gap == null || gap < gapShut)
        fun open(open: Float) = open > settings.openAbove
        val current = when {
            shut(left, dots?.leftShape) && open(right) -> Wink.Left
            shut(right, dots?.rightShape) && open(left) -> Wink.Right
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
}
