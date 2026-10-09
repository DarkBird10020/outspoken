package com.outspoken.blink

import com.outspoken.eye.EyeSample
import com.outspoken.eye.FaceDots
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
            hold(500) { EyeSample(time, faceFound = false) } +
            hold(200) { eyes(0.05f) } +
            hold(100) { eyes(0.95f) }
        assertEquals(listOf(BlinkEvent.FaceLost, BlinkEvent.FaceFound), events)
    }

    @Test
    fun `a short face dropout keeps the blink going`() {
        hold(500) { eyes(0.95f) }
        val events = hold(200) { eyes(0.05f) } +
            hold(100) { EyeSample(time, faceFound = false) } +
            hold(200) { eyes(0.05f) } +
            hold(100) { eyes(0.95f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `a short face dropout is not face lost`() {
        hold(100) { eyes(0.95f) }
        assertEquals(emptyList<BlinkEvent>(), hold(300) { EyeSample(time, faceFound = false) })
        assertTrue(detector.tracking)
    }

    @Test
    fun `turning away counts as face lost`() {
        hold(100) { eyes(0.95f) }
        assertEquals(listOf(BlinkEvent.FaceLost), hold(500) { eyes(0.95f, yaw = 40f) })
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

    private fun withGap(open: Float, gap: Float) =
        EyeSample(time, true, open, open, dots = FaceDots(emptyList(), emptyList(), 0.75f, gap, gap))

    private val gapSettings = BlinkSettings(
        closedBelow = 0.68f,
        openAbove = 0.78f,
        minBlinkMs = 400,
        maxBlinkMs = 1_500,
        shapeClosedBelow = 0.13f,
        shapeOpenAbove = 0.18f,
    )

    @Test
    fun `looking down with the lids half open is not a close`() {
        detector.settings = gapSettings
        hold(500) { withGap(0.9f, 0.30f) }
        // Phone log: looking down read 0.55 to 0.65 open with a lid gap of about 0.15.
        val events = hold(1_000) { withGap(0.6f, 0.15f) } + hold(300) { withGap(0.9f, 0.30f) }
        assertEquals(emptyList<BlinkEvent>(), events)
    }

    @Test
    fun `a real close passes both checks`() {
        detector.settings = gapSettings
        hold(500) { withGap(0.9f, 0.30f) }
        val events = hold(500) { withGap(0.45f, 0.06f) } + hold(300) { withGap(0.9f, 0.30f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `opening the lids ends a close while looking down`() {
        detector.settings = gapSettings
        hold(500) { withGap(0.9f, 0.30f) }
        val events = hold(500) { withGap(0.45f, 0.06f) } + hold(300) { withGap(0.6f, 0.22f) }
        val blink = events.single() as BlinkEvent.Blink
        assertTrue(blink.durationMs in 495..530)
    }
}
