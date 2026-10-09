package com.outspoken.conversation

import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Test

/** The highlight moved by the eyes: look up for the next card, blink to say it. */
class EyeModeTest {

    private val said = mutableListOf<String>()
    private val controller = ConversationController(speak = { said += it }).apply { moveByEyes = true }
    private var time = 10_000L

    /** One frame per 33 ms. Up is negative gaze y. */
    private fun frames(ms: Long, open: Float = 0.95f, gazeY: Float = 0f) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, true, open, open, gaze = Dot(0f, gazeY)))
            time += 33
        }
    }

    private fun lookUp() {
        frames(500, gazeY = -0.7f)
        frames(300)
    }

    private fun blink() {
        frames(500, open = 0.05f)
        frames(100)
    }

    private val highlighted get() = controller.ui.value.highlighted

    @Test
    fun `the highlight waits for the eyes`() {
        frames(5_000)
        assertEquals(0, highlighted)
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun `looking up moves to the next card`() {
        frames(500)
        lookUp()
        assertEquals(1, highlighted)
    }

    @Test
    fun `looking down does nothing`() {
        frames(500)
        frames(800, gazeY = 0.4f)
        assertEquals(0, highlighted)
    }

    @Test
    fun `next after the last card goes back to the first`() {
        frames(500)
        repeat(5) { lookUp() }
        assertEquals(Board.YES_NO, highlighted)
        lookUp()
        assertEquals(0, highlighted)
    }

    @Test
    fun `a blink says the card the eyes moved to`() {
        frames(500)
        lookUp()
        blink()
        assertEquals(listOf("I am in pain"), said)
    }

    @Test
    fun `after speaking the highlight goes back to the top`() {
        frames(500)
        lookUp()
        blink()
        controller.onSpeechDone(time)
        assertEquals(0, highlighted)
    }
}
