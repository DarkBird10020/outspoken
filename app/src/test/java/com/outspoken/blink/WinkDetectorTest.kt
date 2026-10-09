package com.outspoken.blink

import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Test

class WinkDetectorTest {

    private val detector = WinkDetector(holdMs = 300)
    private val settings = BlinkSettings(closedBelow = 0.3f, openAbove = 0.5f)
    private var time = 0L

    private fun hold(ms: Long, left: Float, right: Float): List<Wink> {
        val winks = mutableListOf<Wink>()
        val end = time + ms
        while (time < end) {
            detector.onSample(EyeSample(time, true, left, right), settings)?.let(winks::add)
            time += 33
        }
        return winks
    }

    @Test
    fun `a held left wink fires once`() {
        hold(300, 0.95f, 0.95f)
        assertEquals(listOf(Wink.Left), hold(800, 0.05f, 0.95f))
    }

    @Test
    fun `a held right wink fires once`() {
        hold(300, 0.95f, 0.95f)
        assertEquals(listOf(Wink.Right), hold(800, 0.95f, 0.05f))
    }

    @Test
    fun `a short wink does nothing`() {
        hold(300, 0.95f, 0.95f)
        assertEquals(emptyList<Wink>(), hold(200, 0.05f, 0.95f))
    }

    @Test
    fun `a blink is not a wink even when one eye shuts first`() {
        hold(300, 0.95f, 0.95f)
        val winks = hold(66, 0.05f, 0.95f) + hold(500, 0.05f, 0.05f) + hold(300, 0.95f, 0.95f)
        assertEquals(emptyList<Wink>(), winks)
    }

    @Test
    fun `the other eye half shut is not a wink`() {
        hold(300, 0.95f, 0.95f)
        assertEquals(emptyList<Wink>(), hold(800, 0.05f, 0.4f))
    }

    @Test
    fun `two winks in a row fire twice`() {
        hold(300, 0.95f, 0.95f)
        val winks = hold(500, 0.05f, 0.95f) + hold(300, 0.95f, 0.95f) + hold(500, 0.05f, 0.95f)
        assertEquals(listOf(Wink.Left, Wink.Left), winks)
    }
}
