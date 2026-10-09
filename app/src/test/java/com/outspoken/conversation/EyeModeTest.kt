package com.outspoken.conversation

import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Test

/** The highlight moved by the eyes: look down for the next card, up for the previous, blink to say. */
class EyeModeTest {

    private val said = mutableListOf<String>()
    private val controller = ConversationController(speak = { said += it }).apply { moveByEyes = true }
    private var time = 10_000L

    private fun frames(ms: Long, open: Float = 0.95f, gazeY: Float = 0f) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, true, open, open, gaze = Dot(0f, gazeY)))
            time += 33
        }
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
    fun `looking down moves to the next card`() {
        frames(500)
        frames(500, gazeY = 0.4f)
        assertEquals(1, highlighted)
    }

    @Test
    fun `looking up from the top wraps to the last card`() {
        frames(500)
        frames(500, gazeY = -0.4f)
        assertEquals(Board.YES_NO, highlighted)
    }

    @Test
    fun `a blink says the card the eyes moved to`() {
        frames(500)
        frames(500, gazeY = 0.4f)
        frames(300)
        blink()
        assertEquals(listOf("I am in pain"), said)
    }

    @Test
    fun `after speaking the highlight goes back to the top`() {
        frames(500)
        frames(500, gazeY = 0.4f)
        frames(300)
        blink()
        controller.onSpeechDone(time)
        assertEquals(0, highlighted)
    }
}
