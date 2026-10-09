package com.outspoken.eye

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * How open an eye looks from its outline: average height over width, the idea behind the eye
 * aspect ratio (Soukupová and Čech, 2016). Height comes from the outline's area as if it were an
 * ellipse, so it uses every point and does not depend on which point is which. An open eye
 * measures roughly 0.25 to 0.4, a shut one close to 0. Null when the outline cannot be measured.
 *
 * [outline] must be in pixels and in order around the eye, as [FaceMesh] lists it.
 */
fun eyeShape(outline: List<Dot>): Float? {
    if (outline.size < 3) return null
    var width = 0f
    for (i in outline.indices) {
        for (j in i + 1 until outline.size) {
            width = maxOf(width, hypot(outline[i].x - outline[j].x, outline[i].y - outline[j].y))
        }
    }
    if (width <= 0f) return null
    var twiceArea = 0f
    for (i in outline.indices) {
        val a = outline[i]
        val b = outline[(i + 1) % outline.size]
        twiceArea += a.x * b.y - b.x * a.y
    }
    val height = 4 * (abs(twiceArea) / 2) / (PI.toFloat() * width)
    return height / width
}
