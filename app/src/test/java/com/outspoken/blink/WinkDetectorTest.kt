package com.outspoken.blink

import com.outspoken.eye.EyeSample
import com.outspoken.eye.FaceDots
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

    @Test
    fun `a wink counts even when the lid gap of the winking eye stays shallow`() {
        // Phone run 01:56:48: winking eye 0.33 with a lid gap of 0.18 against a 0.10 gap line.
        val calibrated = BlinkSettings(closedBelow = 0.6f, openAbove = 0.75f, shapeClosedBelow = 0.10f, shapeOpenAbove = 0.14f)
        val winks = mutableListOf<Wink>()
        repeat(15) {
            val dots = FaceDots(emptyList(), emptyList(), 0.75f, 0.18f, 0.30f)
            detector.onSample(EyeSample(time, true, 0.33f, 0.98f, dots = dots), calibrated)?.let(winks::add)
            time += 33
        }
        assertEquals(listOf(Wink.Left), winks)
    }

    @Test
    fun `both eyes lowered together is not a wink`() {
        val calibrated = BlinkSettings(closedBelow = 0.6f, openAbove = 0.75f)
        hold(300, 0.95f, 0.95f)
        val winks = mutableListOf<Wink>()
        repeat(30) { detector.onSample(EyeSample(time, true, 0.55f, 0.78f), calibrated)?.let(winks::add); time += 33 }
        assertEquals(emptyList<Wink>(), winks)
    }

    @Test
    fun `one eye half shut reads as one eye lower`() {
        hold(300, 0.95f, 0.95f)
        hold(200, 0.55f, 0.90f)
        assertEquals(true, detector.oneEyeLower)
        hold(300, 0.95f, 0.95f)
        assertEquals(false, detector.oneEyeLower)
    }

    @Test
    fun `eyes that always read apart are not one eye lower`() {
        hold(300, 0.95f, 0.70f)
        assertEquals(false, detector.oneEyeLower)
    }

    @Test
    fun `an eye flickering open mid wink counts once`() {
        hold(300, 0.95f, 0.95f)
        val winks = hold(400, 0.05f, 0.95f) + hold(66, 0.95f, 0.95f) + hold(400, 0.05f, 0.95f)
        assertEquals(listOf(Wink.Left), winks)
    }
}
