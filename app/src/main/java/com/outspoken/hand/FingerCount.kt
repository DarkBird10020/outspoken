package com.outspoken.hand

import kotlin.math.hypot

/** A hand point in pixels. */
data class HandPoint(val x: Float, val y: Float)

/**
 * How many of the four fingers (not the thumb) are held out, from MediaPipe's 21 hand points:
 * 0 is the wrist, and each finger's middle joint and tip are 6 and 8 (index), 10 and 12, 14 and
 * 16, 18 and 20 (little finger). A finger counts as out when its tip is clearly further from the
 * wrist than its middle joint; curled into the palm, the tip comes back nearer the wrist than the
 * joint. Distances, not directions, so a tilted hand counts the same. Null without all 21 points.
 */
fun fingersOut(points: List<HandPoint>): Int? {
    if (points.size < HAND_POINTS) return null
    val wrist = points[0]
    return FINGERS.count { (joint, tip) -> distance(wrist, points[tip]) > OUT_RATIO * distance(wrist, points[joint]) }
}

private fun distance(a: HandPoint, b: HandPoint) = hypot(a.x - b.x, a.y - b.y)

private const val HAND_POINTS = 21

/** Middle joint and tip of the index, middle, ring and little fingers. */
private val FINGERS = listOf(6 to 8, 10 to 12, 14 to 16, 18 to 20)

/** A margin over "just further", so a half-bent finger is not counted. */
private const val OUT_RATIO = 1.15f
