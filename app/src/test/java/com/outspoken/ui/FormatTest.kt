package com.outspoken.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `seconds keep one decimal`() {
        assertEquals("1.2 s", formatSeconds(1.2f))
        assertEquals("0.5 s", formatSeconds(0.5f))
        assertEquals("2.0 s", formatSeconds(2f))
    }

    @Test
    fun `whole numbers round and take a unit`() {
        assertEquals("24 tok/s", formatWhole(23.6f, " tok/s"))
        assertEquals("92%", formatWhole(92f, "%"))
    }

    @Test
    fun `unknown values show a dash`() {
        assertEquals("-", formatSeconds(null))
        assertEquals("-", formatWhole(null, "%"))
    }

    @Test
    fun `clock shows minutes and seconds`() {
        assertEquals("00:00", formatClock(0))
        assertEquals("04:12", formatClock(252_000))
        assertEquals("61:01", formatClock(3_661_000))
    }
}
