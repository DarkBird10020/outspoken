package com.outspoken.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitStatsTest {

    private val stats = PitStats(startedMs = 1_000)

    @Test
    fun `nothing measured yet`() {
        assertNull(stats.replyTimeSeconds)
        assertNull(stats.tokensPerSecond)
        assertEquals(0, stats.repliesWritten)
    }

    @Test
    fun `model answers are averaged and counted`() {
        stats.onReplies(1_200, fromModel = true, tokensPerSecond = 20f)
        stats.onReplies(1_800, fromModel = true, tokensPerSecond = 30f)
        assertEquals(1.5f, stats.replyTimeSeconds!!, 0.001f)
        assertEquals(25f, stats.tokensPerSecond!!, 0.001f)
        assertEquals(8, stats.repliesWritten)
    }

    @Test
    fun `fallbacks are kept apart from model answers`() {
        stats.onReplies(300, fromModel = false, tokensPerSecond = null)
        assertNull(stats.replyTimeSeconds)
        assertEquals(1, stats.fallbackCount)
    }

    @Test
    fun `answers without a speed still count`() {
        stats.onReplies(1_000, fromModel = true, tokensPerSecond = null)
        assertEquals(4, stats.repliesWritten)
        assertNull(stats.tokensPerSecond)
    }

    @Test
    fun `session length runs from the start`() {
        assertEquals(251_000, stats.sessionMillis(252_000))
        assertEquals(0, stats.sessionMillis(0))
    }
}
