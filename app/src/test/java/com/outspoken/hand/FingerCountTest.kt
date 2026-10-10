package com.outspoken.hand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FingerCountTest {

    /**
     * A hand of 21 points with the wrist at (200, 400), fingers pointing [angleDeg] from straight
     * up. Each finger's middle joint sits 80 px from the wrist; its tip 120 px out when held out,
     * or 50 px (back in the palm) when curled. The thumb is curled in every case.
     */
    private fun hand(out: List<Boolean>, angleDeg: Double = 0.0): List<HandPoint> {
        val points = MutableList(21) { HandPoint(200f, 400f) }
        val spreads = listOf(-15.0, -5.0, 5.0, 15.0)
        val joints = listOf(6 to 8, 10 to 12, 14 to 16, 18 to 20)
        joints.forEachIndexed { i, (joint, tip) ->
            val angle = Math.toRadians(angleDeg + spreads[i])
            fun at(distance: Double) = HandPoint((200 + distance * sin(angle)).toFloat(), (400 - distance * cos(angle)).toFloat())
            points[joint] = at(80.0)
            points[tip] = at(if (out[i]) 120.0 else 50.0)
        }
        return points
    }

    @Test
    fun `fingers held out are counted, curled ones are not`() {
        assertEquals(0, fingersOut(hand(listOf(false, false, false, false))))
        assertEquals(1, fingersOut(hand(listOf(true, false, false, false))))
        assertEquals(2, fingersOut(hand(listOf(true, true, false, false))))
        assertEquals(3, fingersOut(hand(listOf(true, true, true, false))))
        assertEquals(4, fingersOut(hand(listOf(true, true, true, true))))
    }

    @Test
    fun `a tilted or sideways hand counts the same`() {
        assertEquals(3, fingersOut(hand(listOf(true, true, true, false), angleDeg = 40.0)))
        assertEquals(2, fingersOut(hand(listOf(true, true, false, false), angleDeg = -90.0)))
    }

    @Test
    fun `a half bent finger, tip only a little past its joint, is not counted`() {
        val points = hand(listOf(true, false, false, false)).toMutableList()
        // Index tip at 88 px from the wrist against its joint's 80: under the 1.15 margin.
        points[8] = HandPoint(200f + (88 * sin(Math.toRadians(-15.0))).toFloat(), 400f - (88 * cos(Math.toRadians(-15.0))).toFloat())
        assertEquals(0, fingersOut(points))
    }

    @Test
    fun `without all 21 points there is no count`() {
        assertNull(fingersOut(emptyList()))
    }

    @Test
    fun `the model's labels and the count make the signs`() {
        assertEquals(HandSign.ThumbUp, HandSign.read("Thumb_Up", 0))
        assertEquals(HandSign.ClosedFist, HandSign.read("Closed_Fist", 0))
        assertEquals(HandSign.OneFinger, HandSign.read("Pointing_Up", 1))
        assertEquals(HandSign.TwoFingers, HandSign.read("Victory", 2))
        assertEquals(HandSign.FourFingers, HandSign.read("Open_Palm", 4))
        // Three fingers has no label of the model's: the count decides.
        assertEquals(HandSign.ThreeFingers, HandSign.read("None", 3))
        assertNull(HandSign.read("None", 0))
        assertNull(HandSign.read(null, null))
        assertEquals(HandAction.Light(2), HandSign.ThreeFingers.action)
        assertEquals(HandSign.ThreeFingers, HandSign.fromKey("Three_Fingers"))
    }
}
