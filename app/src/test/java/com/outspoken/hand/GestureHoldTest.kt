package com.outspoken.hand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureHoldTest {

    private val hold = GestureHold(holdMs = 600, gapMs = 250, releaseMs = 500, minScore = 0.6f)
    private var time = 1_000L

    /** Frames every 83 ms, about what every second camera frame gives. Returns the signs that fired. */
    private fun show(sign: HandSign?, ms: Long, score: Float = 0.9f, allowed: Set<HandSign> = HandSign.entries.toSet()): List<HandSign> {
        val fired = mutableListOf<HandSign>()
        val end = time + ms
        while (time < end) {
            hold.onFrame(sign, score, time, allowed)?.let(fired::add)
            time += 83
        }
        return fired
    }

    @Test
    fun `a sign held long enough fires once`() {
        assertEquals(listOf(HandSign.ThumbUp), show(HandSign.ThumbUp, 800))
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
    fun `a different sign in the middle starts the count again`() {
        show(HandSign.ThumbUp, 400)
        show(HandSign.ThumbDown, 83)
        assertEquals(emptyList<HandSign>(), show(HandSign.ThumbUp, 400))
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
