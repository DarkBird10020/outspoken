package com.outspoken.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkTallyTest {

    @Test
    fun `no tries shows nothing`() {
        assertNull(BlinkTally().percent)
    }

    @Test
    fun `share caught out of all tries`() {
        assertEquals(75f, BlinkTally(caught = 3, missed = 1).percent!!, 0.01f)
        assertEquals(0f, BlinkTally(caught = 0, missed = 2).percent!!, 0.01f)
    }

    @Test
    fun `practice and conversation tries add up`() {
        val total = BlinkTally(caught = 3, missed = 1) + BlinkTally(caught = 5, missed = 1)
        assertEquals(BlinkTally(caught = 8, missed = 2), total)
        assertEquals(80f, total.percent!!, 0.01f)
    }
}
