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

    @Test
    fun `holding the eyes shut two seconds gives the cue once`() {
        assertEquals(listOf(HelpStep.HoldReached), shut(1_000, 3_500))
        assertTrue(trigger.armed)
    }

    @Test
    fun `a blink after the hold sounds the alarm`() {
        shut(1_000, 3_500)
        assertEquals(HelpStep.Alarm, trigger.onBlink(4_000))
        assertFalse(trigger.armed)
    }

    @Test
    fun `a shorter close does nothing`() {
        assertEquals(emptyList<HelpStep>(), shut(1_000, 2_500))
        assertNull(trigger.onBlink(3_000))
    }

    @Test
    fun `no blink within five seconds cancels`() {
        shut(1_000, 3_500)
        trigger.onEyes(null, 9_000)
        assertFalse(trigger.armed)
        assertNull(trigger.onBlink(9_100))
    }

    @Test
    fun `the hold itself is never a pick`() {
        assertTrue(trigger.isHold(2_000))
        assertFalse(trigger.isHold(1_500))
    }
}
