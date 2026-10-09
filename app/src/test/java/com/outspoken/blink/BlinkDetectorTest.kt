package com.outspoken.blink

import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkDetectorTest {

    private val detector = BlinkDetector()
    private var time = 1_000L

    private fun eyes(left: Float, right: Float = left, yaw: Float = 0f) =
        EyeSample(time, faceFound = true, leftOpen = left, rightOpen = right, yawDeg = yaw)

    /** Feeds one sample per 33 ms frame for [ms] and returns the events seen. */
    private fun hold(ms: Long, sample: () -> EyeSample): List<BlinkEvent> {
        val events = mutableListOf<BlinkEvent>()
        val end = time + ms
        while (time < end) {
            detector.onSample(sample())?.let(events::add)
            time += 33
        }
        return events
    }

    private fun blink(closedMs: Long, left: Float = 0.05f, right: Float = 0.05f): List<BlinkEvent> {
        hold(500) { eyes(0.95f) }
        return hold(closedMs) { eyes(left, right) } + hold(300) { eyes(0.95f) }
    }

    @Test
    fun `first face reports found`() {
        assertEquals(listOf(BlinkEvent.FaceFound), hold(100) { eyes(0.95f) })
        assertTrue(detector.tracking)
    }

    @Test
    fun `half second blink is intentional`() {
        val events = blink(500)
        val blink = events.single() as BlinkEvent.Blink
        assertTrue(blink.durationMs in 495..530)
    }

    @Test
    fun `blink event starts when the eyes shut`() {
        hold(500) { eyes(0.95f) }
        val shutAt = time
        val events = hold(500) { eyes(0.05f) } + hold(100) { eyes(0.95f) }
        assertEquals(shutAt, (events.single() as BlinkEvent.Blink).startMs)
    }

    @Test
    fun `normal fast blink is ignored`() {
        assertEquals(emptyList<BlinkEvent>(), blink(150))
    }

    @Test
    fun `long closure is ignored`() {
        assertEquals(emptyList<BlinkEvent>(), blink(1_500))
    }

    @Test
    fun `wink does not count`() {
        assertEquals(emptyList<BlinkEvent>(), blink(500, left = 0.05f, right = 0.95f))
    }

    @Test
    fun `values between the lines keep the eyes shut`() {
        hold(500) { eyes(0.95f) }
        val events = hold(200) { eyes(0.05f) } + hold(300) { eyes(0.4f) } + hold(100) { eyes(0.95f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `losing the face cancels a blink`() {
        hold(500) { eyes(0.95f) }
        val events = hold(200) { eyes(0.05f) } +
            hold(100) { EyeSample(time, faceFound = false) } +
            hold(200) { eyes(0.05f) } +
            hold(100) { eyes(0.95f) }
        assertEquals(listOf(BlinkEvent.FaceLost, BlinkEvent.FaceFound), events)
    }

    @Test
    fun `turning away counts as face lost`() {
        hold(100) { eyes(0.95f) }
        assertEquals(listOf(BlinkEvent.FaceLost), hold(100) { eyes(0.95f, yaw = 40f) })
        assertFalse(detector.tracking)
    }

    @Test
    fun `window edges are inclusive`() {
        detector.settings = BlinkSettings(minBlinkMs = 300, maxBlinkMs = 900)
        detector.onSample(EyeSample(0, true, 0.95f, 0.95f))
        detector.onSample(EyeSample(100, true, 0.05f, 0.05f))
        assertTrue(detector.onSample(EyeSample(400, true, 0.95f, 0.95f)) is BlinkEvent.Blink)
        detector.onSample(EyeSample(1_000, true, 0.05f, 0.05f))
        assertTrue(detector.onSample(EyeSample(1_900, true, 0.95f, 0.95f)) is BlinkEvent.Blink)
        detector.onSample(EyeSample(3_000, true, 0.05f, 0.05f))
        assertNull(detector.onSample(EyeSample(3_901, true, 0.95f, 0.95f)))
    }
}
