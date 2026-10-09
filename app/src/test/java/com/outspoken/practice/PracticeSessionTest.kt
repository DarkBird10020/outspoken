package com.outspoken.practice

import com.outspoken.blink.BlinkDetector
import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionTest {

    private val detector = BlinkDetector()
    private var time = 0L
    private val session = PracticeSession(
        detector,
        startMs = 0,
        scanIntervalMs = 1_200,
        measureMs = 3_000,
        litMs = 2_500,
        gapMs = 1_000,
    )

    private fun frames(ms: Long, open: Float, faceFound: Boolean = true) {
        val end = time + ms
        while (time < end) {
            session.onSample(EyeSample(time, faceFound, open, open))
            session.update(time)
            time += 33
        }
    }

    private fun blink() {
        frames(700, open = 0.02f)
        frames(100, open = 0.6f)
    }

    private fun waitForStar() {
        while (session.update(time).starAt < 0) frames(33, open = 0.6f)
    }

    private fun measure() = frames(3_100, open = 0.6f)

    @Test
    fun `measuring sets the open level from this person`() {
        frames(100, open = 0.6f)
        assertEquals("Measuring", session.update(time).eyeState)
        measure()
        assertEquals(0.6f, detector.openLevel, 0.01f)
        assertEquals("Steady", session.update(time).eyeState)
    }

    @Test
    fun `measuring waits for the face`() {
        frames(4_000, open = 0.6f, faceFound = false)
        assertEquals("Looking for you", session.update(time).eyeState)
        assertEquals("Keep your eyes open while it measures", session.update(time).progressNote)
    }

    @Test
    fun `natural blinks while measuring set the hold time`() {
        frames(1_000, open = 0.6f)
        frames(330, open = 0.02f)
        frames(2_000, open = 0.6f)
        assertTrue(detector.settings.minBlinkMs in 480L..560L)
    }

    @Test
    fun `no natural blinks keeps the default hold time`() {
        measure()
        assertEquals(500L, detector.settings.minBlinkMs)
    }

    @Test
    fun `three caught blinks finish the practice`() {
        measure()
        repeat(3) {
            waitForStar()
            blink()
        }
        assertEquals(3, session.caught)
        assertTrue(session.done)
        assertEquals("You are ready", session.update(time).progressNote)
        assertEquals(100f, session.accuracyPercent!!, 0.01f)
    }

    @Test
    fun `the star moves to the next place each round`() {
        measure()
        waitForStar()
        val first = session.update(time).starAt
        blink()
        waitForStar()
        assertEquals((first + 1) % 3, session.update(time).starAt)
        assertEquals("Two more and you are ready", session.update(time).progressNote)
    }

    @Test
    fun `blinking with no star and missing a star both lower accuracy`() {
        measure()
        waitForStar()
        frames(2_600, open = 0.6f)
        assertEquals(1, session.missed)
        while (session.update(time).starAt >= 0) frames(33, open = 0.6f)
        blink()
        assertEquals(1, session.falseBlinks)
        waitForStar()
        blink()
        assertEquals(1, session.caught)
        assertEquals(100f / 3, session.accuracyPercent!!, 0.01f)
    }

    @Test
    fun `no star and no misses while the face is away`() {
        measure()
        frames(10_000, open = 0.6f, faceFound = false)
        assertEquals(0, session.missed)
        assertEquals(-1, session.update(time).starAt)
    }

    @Test
    fun `practice screen shows the live eye level and the blink line`() {
        measure()
        val ui = session.update(time)
        assertEquals(0.6f, ui.eyeOpen, 0.01f)
        assertEquals(detector.closedBelow, ui.blinkLevel, 0.0001f)
        assertEquals(1.2f, ui.scanSpeedSeconds, 0.0001f)
    }
}
