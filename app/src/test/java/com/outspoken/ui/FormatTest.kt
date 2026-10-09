package com.outspoken.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `reply line says where replies came from`() {
        assertEquals("1.4 s from the model, 24 tok/s", describeReplies(1_400, fromModel = true, tokensPerSecond = 23.8f))
        assertEquals("2.1 s from the model", describeReplies(2_100, fromModel = true, tokensPerSecond = null))
        assertTrue(describeReplies(300, fromModel = false, tokensPerSecond = null).startsWith("0.3 s, phrase bank"))
    }
}
