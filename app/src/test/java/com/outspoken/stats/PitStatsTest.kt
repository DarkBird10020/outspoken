package com.outspoken.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitStatsTest {

    private val stats = PitStats(startedMs = 1_000)

    @Test
    fun `nothing measured yet`() {
        assertNull(stats.replyTimeSeconds(5_000))
        assertNull(stats.tokensPerSecond)
        assertEquals(0, stats.repliesWritten)
    }

    @Test
    fun `the latest model answer shows, not an average`() {
        stats.onReplies(1_200, fromModel = true, tokensPerSecond = 20f)
        stats.onReplies(1_800, fromModel = true, tokensPerSecond = 30f)
        assertEquals(1.8f, stats.replyTimeSeconds(10_000)!!, 0.001f)
        assertEquals(30f, stats.tokensPerSecond!!, 0.001f)
        assertEquals(8, stats.repliesWritten)
    }

    @Test
    fun `reply time counts up while the model writes, then holds`() {
        stats.onReplies(1_500, fromModel = true, tokensPerSecond = 40f)
        stats.onAsked(10_000)
        assertEquals(0f, stats.replyTimeSeconds(10_000)!!, 0.001f)
        assertEquals(0.4f, stats.replyTimeSeconds(10_400)!!, 0.001f)
        stats.onReplies(900, fromModel = true, tokensPerSecond = 60f)
        assertEquals(0.9f, stats.replyTimeSeconds(20_000)!!, 0.001f)
        assertEquals(60f, stats.tokensPerSecond!!, 0.001f)
    }

    @Test
    fun `a newer question starts the count again`() {
        stats.onAsked(10_000)
        stats.onAsked(10_500)
        assertEquals(0.2f, stats.replyTimeSeconds(10_700)!!, 0.001f)
    }

    @Test
    fun `fallbacks are kept apart from model answers`() {
        stats.onReplies(1_000, fromModel = true, tokensPerSecond = 25f)
        stats.onAsked(10_000)
        stats.onReplies(300, fromModel = false, tokensPerSecond = null)
        assertEquals(1f, stats.replyTimeSeconds(20_000)!!, 0.001f)
        assertEquals(1, stats.fallbackCount)
        assertEquals(4, stats.repliesWritten)
    }

    @Test
    fun `answers without a speed still count and keep the last speed`() {
        stats.onReplies(1_000, fromModel = true, tokensPerSecond = 40f)
        stats.onReplies(1_100, fromModel = true, tokensPerSecond = null)
        assertEquals(8, stats.repliesWritten)
        assertEquals(40f, stats.tokensPerSecond!!, 0.001f)
    }

    @Test
    fun `session length runs from the start`() {
        assertEquals(251_000, stats.sessionMillis(252_000))
        assertEquals(0, stats.sessionMillis(0))
    }
}
