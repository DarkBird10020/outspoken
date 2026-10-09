package com.outspoken.blink

import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkDetectorTest {

    private val detector = BlinkDetector()
    private var time = 1_000L

    private fun eyes(left: Float, right: Float = left, yaw: Float = 0f, pitch: Float = 0f) =
        EyeSample(time, faceFound = true, leftOpen = left, rightOpen = right, yawDeg = yaw, pitchDeg = pitch)

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

    /** Eyes open, shut for [closedMs], open again. Returns events after the first open stretch. */
    private fun blink(closedMs: Long, left: Float = 0.05f, right: Float = 0.05f, open: Float = 0.95f): List<BlinkEvent> {
        hold(1_000) { eyes(open) }
        return hold(closedMs) { eyes(left, right) } + hold(300) { eyes(open) }
    }

    @Test
    fun `first face reports found`() {
        assertEquals(listOf(BlinkEvent.FaceFound), hold(100) { eyes(0.95f) })
        assertTrue(detector.tracking)
    }

    @Test
    fun `seven tenths of a second is a blink`() {
        val blink = blink(700).single() as BlinkEvent.Blink
        assertTrue(blink.durationMs in 690..740)
    }

    @Test
    fun `blink starts when the eyes shut`() {
        hold(1_000) { eyes(0.95f) }
        val shutAt = time
        val events = hold(700) { eyes(0.05f) } + hold(200) { eyes(0.95f) }
        assertEquals(shutAt, (events.single() as BlinkEvent.Blink).startMs)
    }

    @Test
    fun `normal blinks are ignored`() {
        listOf(150L, 300L, 400L).forEach { assertTrue(blink(it).single() is BlinkEvent.Rejected) }
    }

    @Test
    fun `long closure is ignored`() {
        assertTrue(blink(1_800).single() is BlinkEvent.Rejected)
    }

    @Test
    fun `wink does not count`() {
        assertEquals(emptyList<BlinkEvent>(), blink(700, left = 0.05f, right = 0.95f))
    }

    @Test
    fun `one bright frame does not split a blink`() {
        hold(1_000) { eyes(0.95f) }
        val events = hold(300) { eyes(0.05f) } + hold(33) { eyes(0.95f) } + hold(300) { eyes(0.05f) } + hold(200) { eyes(0.95f) }
        assertTrue((events.single() as BlinkEvent.Blink).durationMs > 600)
    }

    @Test
    fun `lines follow a person whose eyes look half shut`() {
        hold(20_000) { eyes(0.4f) }
        assertTrue(detector.openLevel < 0.45f)
        assertTrue(detector.closedBelow < 0.2f)
        val events = hold(700) { eyes(0.02f) } + hold(200) { eyes(0.4f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `half shut lids alone are not a blink`() {
        hold(20_000) { eyes(0.4f) }
        assertEquals(emptyList<BlinkEvent>(), hold(2_000) { eyes(if ((time / 33) % 2 == 0L) 0.35f else 0.45f) })
    }

    @Test
    fun `lids stuck below the line become the new open level`() {
        hold(1_000) { eyes(0.95f) }
        hold(BlinkDetector.STUCK_MS + 500) { eyes(0.3f) }
        assertFalse(detector.eyesShut)
        assertTrue(detector.closedBelow < 0.3f)
    }

    @Test
    fun `short face dropout is ignored`() {
        hold(1_000) { eyes(0.95f) }
        val events = hold(300) { eyes(0.05f) } +
            hold(200) { EyeSample(time, faceFound = false) } +
            hold(300) { eyes(0.05f) } +
            hold(200) { eyes(0.95f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `long face loss reports lost and cancels the blink`() {
        hold(1_000) { eyes(0.95f) }
        val events = hold(300) { eyes(0.05f) } +
            hold(800) { EyeSample(time, faceFound = false) } +
            hold(300) { eyes(0.95f) }
        assertEquals(listOf(BlinkEvent.FaceLost, BlinkEvent.FaceFound), events)
    }

    @Test
    fun `turning far away counts as face lost`() {
        hold(500) { eyes(0.95f) }
        assertEquals(listOf(BlinkEvent.FaceLost), hold(1_000) { eyes(0.95f, yaw = 45f) })
        assertFalse(detector.tracking)
    }

    @Test
    fun `looking down at a phone below eye level still tracks`() {
        assertEquals(listOf(BlinkEvent.FaceFound), hold(1_000) { eyes(0.95f, pitch = -28f) })
    }

    @Test
    fun `recent closures are kept for the check screen`() {
        blink(200)
        blink(700)
        assertEquals(listOf(false, true), detector.recentClosures.map { it.accepted })
    }
}
