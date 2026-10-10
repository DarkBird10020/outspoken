package com.outspoken.hand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureHoldTest {

    private val hold = GestureHold(holdMs = 600, chooseHoldMs = 1_000, gapMs = 250, releaseMs = 500, minScore = 0.6f, minShare = 0.7f)
    private var time = 1_000L
    private val fired = mutableListOf<HeldSign>()

    /** Frames every 83 ms, about what every second camera frame gives. Returns the signs that fired. */
    private fun show(sign: HandSign?, ms: Long, score: Float = 0.9f, allowed: Set<HandSign> = HandSign.entries.toSet()): List<HandSign> {
        val signs = mutableListOf<HandSign>()
        val end = time + ms
        while (time < end) {
            hold.onFrame(sign, score, time, allowed)?.let {
                fired += it
                signs += it.sign
            }
            time += 83
        }
        return signs
    }

    /** Two signs read in turn, frame by frame, as the model did while a finger was raised. */
    private fun flip(a: HandSign, b: HandSign, ms: Long): List<HandSign> {
        val signs = mutableListOf<HandSign>()
        val end = time + ms
        var useA = true
        while (time < end) {
            hold.onFrame(if (useA) a else b, 0.9f, time)?.let { signs += it.sign }
            useA = !useA
            time += 83
        }
        return signs
    }

    @Test
    fun `a sign held long enough fires once`() {
        assertEquals(listOf(HandSign.ThumbUp), show(HandSign.ThumbUp, 800))
        assertTrue(fired.single().heldMs >= 600)
        assertEquals(fired.single().readings, fired.single().steadyReadings)
    }

    @Test
    fun `a sign shown too briefly does nothing`() {
        assertEquals(emptyList<HandSign>(), show(HandSign.ThumbUp, 400))
        assertEquals(emptyList<HandSign>(), show(null, 400))
    }

    @Test
    fun `a sign held on fires only once`() {
        assertEquals(listOf(HandSign.Victory), show(HandSign.Victory, 3_000))
    }

    @Test
    fun `the same sign fires again only after the hand drops`() {
        show(HandSign.ThumbUp, 800)
        assertEquals(emptyList<HandSign>(), show(null, 300))
        assertEquals(emptyList<HandSign>(), show(HandSign.ThumbUp, 800))
        show(null, 600)
        assertEquals(listOf(HandSign.ThumbUp), show(HandSign.ThumbUp, 800))
    }

    @Test
    fun `after a sign fires a different one can follow without lowering the hand`() {
        assertEquals(listOf(HandSign.ThumbUp), show(HandSign.ThumbUp, 800))
        assertEquals(listOf(HandSign.ThumbDown), show(HandSign.ThumbDown, 900))
    }

    @Test
    fun `one frame read as another sign does not break a hold`() {
        show(HandSign.ThumbUp, 400)
        show(HandSign.ThumbDown, 83)
        assertEquals(listOf(HandSign.ThumbUp), show(HandSign.ThumbUp, 400))
    }

    @Test
    fun `another sign shown instead takes over with its own hold`() {
        assertEquals(emptyList<HandSign>(), show(HandSign.ThumbUp, 400))
        assertEquals(listOf(HandSign.ThumbDown), show(HandSign.ThumbDown, 1_300))
    }

    @Test
    fun `readings flipping between a point and a fist fire neither until one is steady`() {
        // Phone, 15:33:13 to 15:33:17: a raised finger read as Pointing_Up and Closed_Fist in turn,
        // and the fist fired, saying the lit card.
        assertEquals(emptyList<HandSign>(), flip(HandSign.PointingUp, HandSign.ClosedFist, 2_000))
        assertEquals(listOf(HandSign.PointingUp), show(HandSign.PointingUp, 900))
    }

    @Test
    fun `a fist needs a longer hold, as it says the lit card`() {
        assertEquals(emptyList<HandSign>(), show(HandSign.ClosedFist, 900))
        assertEquals(listOf(HandSign.ClosedFist), show(HandSign.ClosedFist, 300))
    }

    @Test
    fun `one dropped frame does not break a hold`() {
        show(HandSign.ThumbDown, 400)
        show(null, 83)
        assertEquals(listOf(HandSign.ThumbDown), show(HandSign.ThumbDown, 300))
    }

    @Test
    fun `unsure readings do not count`() {
        assertEquals(emptyList<HandSign>(), show(HandSign.ThumbUp, 1_000, score = 0.4f))
    }

    @Test
    fun `a sign switched off for this person never fires`() {
        val noFist = HandSign.entries.toSet() - HandSign.ClosedFist
        assertEquals(emptyList<HandSign>(), show(HandSign.ClosedFist, 2_000, allowed = noFist))
    }

    @Test
    fun `labels map to signs and None to nothing`() {
        assertEquals(HandSign.ThumbUp, HandSign.fromLabel("Thumb_Up"))
        assertEquals(HandSign.LoveYou, HandSign.fromLabel("ILoveYou"))
        assertNull(HandSign.fromLabel("None"))
        assertNull(HandSign.fromLabel(null))
        assertEquals(HandAction.Say("Yes"), HandSign.ThumbUp.action)
        assertEquals(HandAction.ChooseLit, HandSign.ClosedFist.action)
    }
}
