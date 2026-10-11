package com.outspoken.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpTriggerTest {

    private val trigger = HelpTrigger(holdMs = 2_000, confirmWithinMs = 5_000)

    /** Eyes shut from [from] to [to], one call per 33 ms frame; returns the steps seen. */
    private fun shut(from: Long, to: Long): List<HelpStep> {
        val steps = mutableListOf<HelpStep>()
        var t = from
        while (t < to) {
            trigger.onEyes(from, t)?.let(steps::add)
            t += 33
        }
        trigger.onEyes(null, to)?.let(steps::add)
        return steps
    }

    /** A deliberate blink from [from] to [to]: the blink comes before the frame's eyes, as in the app. */
    private fun blink(from: Long, to: Long): HelpStep? {
        var t = from
        while (t < to) {
            trigger.onEyes(from, t)
            t += 33
        }
        val step = trigger.onBlink(from)
        trigger.onEyes(null, to)
        return step
    }

    @Test
    fun `holding the eyes shut two seconds gives the cue once`() {
        assertEquals(listOf(HelpStep.HoldReached), shut(1_000, 3_500))
        assertTrue(trigger.armed)
    }

    @Test
    fun `a blink after the hold sounds the alarm`() {
        shut(1_000, 3_500)
        assertEquals(HelpStep.Alarm, blink(4_000, 4_600))
        assertFalse(trigger.armed)
    }

    @Test
    fun `a shorter close does nothing`() {
        assertEquals(emptyList<HelpStep>(), shut(1_000, 2_500))
        assertNull(blink(3_000, 3_600))
    }

    @Test
    fun `no blink within five seconds cancels`() {
        shut(1_000, 3_500)
        trigger.onEyes(null, 9_000)
        assertFalse(trigger.armed)
        assertNull(blink(9_100, 9_700))
    }

    @Test
    fun `closing the eyes again and opening them sounds the alarm once`() {
        shut(1_000, 3_500)
        // Phone, 05:08:31 to 05:08:44: opened, then shut again for 2.8 s instead of blinking.
        assertEquals(listOf(HelpStep.Alarm), shut(6_900, 9_700))
        assertFalse(trigger.armed)
        assertNull(blink(10_500, 11_100))
    }

    @Test
    fun `a long deliberate hold still calls for help`() {
        // 19:53:36 on the phone: held 7.9 s, then a 695 ms blink.
        assertEquals(listOf(HelpStep.HoldReached), shut(1_000, 8_900))
        assertEquals(HelpStep.Alarm, blink(9_300, 10_000))
    }

    @Test
    fun `sleep is not a help call`() {
        // Eyes shut for a minute: the cue once, then rest; waking and blinking calls nothing.
        assertEquals(listOf(HelpStep.HoldReached), shut(1_000, 61_000))
        assertFalse(trigger.armed)
        assertNull(blink(61_500, 62_100))
        assertEquals(emptyList<HelpStep>(), shut(62_500, 64_000))
    }

    @Test
    fun `dozing off again after the hold is not a confirm`() {
        shut(1_000, 3_500)
        // Eyes open, then shut again and stay shut: no alarm, even when they open much later.
        assertEquals(emptyList<HelpStep>(), shut(4_500, 40_000))
        assertFalse(trigger.armed)
    }

    @Test
    fun `a flicker of open eyes is not a confirm`() {
        shut(1_000, 3_500)
        // Open for 100 ms only, as sleep can read, then a close.
        assertNull(blink(3_600, 4_300))
        // Open properly after it, then a blink: that one confirms.
        assertEquals(HelpStep.Alarm, blink(5_000, 5_600))
    }

    @Test
    fun `the face leaving the camera cancels, and does not count as the eyes opening`() {
        var t = 1_000L
        while (t < 3_200) {
            trigger.onEyes(1_000, t)
            t += 33
        }
        // Before, a lost face cleared the close and armed the alarm as if the eyes had opened.
        trigger.onEyes(null, 3_300, faceSeen = false)
        assertFalse(trigger.armed)
        assertNull(blink(3_800, 4_400))
    }

    @Test
    fun `a second close starting after the five seconds is a new hold`() {
        shut(1_000, 3_500)
        assertEquals(listOf(HelpStep.HoldReached), shut(9_000, 11_500))
    }

    @Test
    fun `the hold itself is never a pick`() {
        assertTrue(trigger.isHold(2_000))
        assertFalse(trigger.isHold(1_500))
    }

    @Test
    fun `with no confirm the hold alone sounds the alarm, once, and is no pick`() {
        // The Settings switch off: the hold alone, as the owner tried first.
        trigger.confirm = false
        assertEquals(listOf(HelpStep.Alarm), shut(1_000, 3_500))
        assertTrue(trigger.isHold(2_500))
        assertFalse(trigger.armed)
        assertNull(blink(4_000, 4_600))
        assertFalse(trigger.isHold(600))
    }
}
