package com.outspoken.tutorial

import com.outspoken.blink.BlinkDetector
import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import com.outspoken.scan.GazeStepper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovePracticeTest {

    private val said = mutableListOf<String>()
    private val practice = MovePractice(BlinkDetector(), GazeStepper(), speak = { said += it })
    private var time = 10_000L

    private val ui get() = practice.ui.value

    /** One frame per 33 ms. Up is negative gaze y. */
    private fun frames(ms: Long, open: Float = 0.95f, gazeY: Float = 0f) {
        val end = time + ms
        while (time < end) {
            practice.onSample(EyeSample(time, true, open, open, gaze = Dot(0f, gazeY)))
            time += 33
        }
    }

    private fun start() {
        practice.start(time)
        frames(2_100)
    }

    private fun lookUp() {
        frames(500, gazeY = -0.7f)
        frames(1_100)
    }

    private fun blink() {
        frames(500, open = 0.05f)
        frames(100)
    }

    /** Waits out what the phone says after a choice. */
    private fun afterChoice() = frames(MovePractice.SPEAK_PAUSE_MS + 100)

    @Test
    fun `starting asks for the first star's card`() {
        start()
        assertEquals("Move to \"Yes\", then close your eyes.", said.single())
        assertEquals(0, ui.highlighted)
        assertEquals(2, ui.target)
    }

    @Test
    fun `a look up moves to the next card`() {
        start()
        lookUp()
        assertEquals(1, ui.highlighted)
    }

    @Test
    fun `closing the eyes on the star reaches it and moves the star on`() {
        start()
        lookUp()
        lookUp()
        blink()
        assertEquals(1, ui.reached)
        assertEquals(3, ui.target)
        assertEquals("Well done. Now move to \"Thank you\".", said.last())
        afterChoice()
        assertEquals(0, ui.highlighted)
    }

    @Test
    fun `a choice off the star is counted as wrong and the star stays`() {
        start()
        lookUp()
        blink()
        assertEquals(0, ui.reached)
        assertEquals(1, ui.wrong)
        assertEquals(2, ui.target)
        assertEquals("That was \"I am in pain\". Move to \"Yes\".", said.last())
    }

    @Test
    fun `three stars finish the practice`() {
        start()
        MovePractice.TARGETS.forEach { target ->
            repeat(target) { lookUp() }
            blink()
            afterChoice()
        }
        assertTrue(ui.done)
        assertEquals(0, ui.wrong)
        assertEquals(-1, ui.highlighted)
        assertEquals("Well done. You can move and choose with your eyes.", said.last())
    }

    @Test
    fun `with looking down on a look up goes back round to the last card`() {
        practice.upMovesNext = false
        start()
        lookUp()
        assertEquals(3, ui.highlighted)
    }

    @Test
    fun `holding the eyes shut for help chooses nothing`() {
        start()
        lookUp()
        lookUp()
        frames(2_300, open = 0.05f)
        frames(300)
        assertEquals(0, ui.reached)
        assertEquals(0, ui.wrong)
    }

    @Test
    fun `in blink only the highlight moves by itself and a close on the star reaches it`() {
        practice.moveByEyes = false
        practice.scanMs = 1_000
        val started = time
        practice.start(time)
        // The scan starts after the opening words; the third card is lit from 2 s on.
        frames(started + MovePractice.SPEAK_PAUSE_MS + 2_200 - time)
        assertEquals(2, ui.highlighted)
        blink()
        assertEquals(1, ui.reached)
        assertFalse(ui.done)
    }
}
