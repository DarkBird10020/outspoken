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
    fun `tenths keep one decimal and take a unit`() {
        assertEquals("36.3 °C", formatTenths(36.319f, " °C"))
        assertEquals("34.0 °C", formatTenths(34f, " °C"))
        assertEquals("-", formatTenths(null, " °C"))
    }

    @Test
    fun `stats line for the log matches the screen`() {
        val ui = StatsUi(
            replyTimeSeconds = 1.354f,
            tokensPerSecond = 44.1f,
            repliesWritten = 28,
            phoneTempCelsius = 35.1f,
            modelName = "gemma-4-E2B-it",
            runtime = "LiteRT-LM on GPU with MTP",
            blinkAccuracyPercent = null,
            sessionMillis = 19_000,
            sentencesSpoken = 0,
        )
        assertEquals(
            "reply 1.4 s, 44 tok/s, 28 replies written, 35.1 °C, blink accuracy -, session 00:19, 0 sentences spoken",
            describeStats(ui),
        )
    }

    @Test
    fun `hand reading for the settings screen`() {
        assertEquals("Seen now: 👍 (0.91)", describeHandReading(com.outspoken.hand.HandSign.ThumbUp, 0.912f))
        assertTrue(describeHandReading(null, 0f).startsWith("No sign seen"))
    }

    @Test
    fun `live line for the main page`() {
        assertEquals(listOf("Reply 0.9 s", "61 tok/s", "8 replies written"), liveStatsLine(0.86f, 61.1f, 8))
        assertEquals(listOf("Reply -", "- tok/s", "0 replies written"), liveStatsLine(null, null, 0))
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
