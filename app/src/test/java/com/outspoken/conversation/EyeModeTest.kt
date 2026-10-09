package com.outspoken.conversation

import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Test

/** The highlight moved by the eyes: look down for the card below, up for the one above, blink to say it. */
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

    /** A look, then a pause at rest longer than the rebound guard. */
    private fun lookDown() {
        frames(500, gazeY = 0.5f)
        frames(800)
    }

    private fun lookUp() {
        frames(500, gazeY = -0.7f)
        frames(800)
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
    fun `looking down moves to the card below`() {
        frames(500)
        lookDown()
        assertEquals(1, highlighted)
    }

    @Test
    fun `looking up from the top wraps to the last card`() {
        frames(500)
        lookUp()
        assertEquals(Board.YES_NO, highlighted)
        lookUp()
        assertEquals(Board.MORE_OPTIONS, highlighted)
    }

    @Test
    fun `looking down then up comes back`() {
        frames(500)
        lookDown()
        lookDown()
        lookUp()
        assertEquals(1, highlighted)
    }

    @Test
    fun `a blink says the card the eyes moved to`() {
        frames(500)
        lookDown()
        blink()
        assertEquals(listOf("I am in pain"), said)
    }

    @Test
    fun `a blink on More options opens the next page`() {
        frames(500)
        lookUp()
        lookUp()
        blink()
        assertEquals(emptyList<String>(), said)
        assertEquals(listOf("I am too hot", "I am too cold", "Thank you"), controller.ui.value.replies)
    }

    @Test
    fun `after speaking the highlight goes back to the top`() {
        frames(500)
        lookDown()
        blink()
        controller.onSpeechDone(time)
        assertEquals(0, highlighted)
    }

    @Test
    fun `a blink still chooses when the iris look down is on`() {
        val said = mutableListOf<String>()
        val stepper = com.outspoken.scan.GazeStepper(com.outspoken.scan.GazeSettings(irisDownStrength = 0.012f))
        val c = ConversationController(speak = { said += it }, gaze = stepper).apply { moveByEyes = true }
        var t = 10_000L
        fun frame(open: Float, iris: Float) {
            c.onSample(EyeSample(t, true, open, open, gaze = Dot(0f, 0.6f), irisY = iris))
            t += 33
        }
        repeat(20) { frame(0.95f, -0.03f) }
        // Eyes shut: the iris reads far down, as on the phone.
        repeat(16) { frame(0.05f, 0.31f) }
        repeat(4) { frame(0.95f, -0.03f) }
        assertEquals(listOf("I need water"), said)
    }

    @Test
    fun `a blink chooses when shut eyes read inside the iris look down range`() {
        val said = mutableListOf<String>()
        val stepper = com.outspoken.scan.GazeStepper(com.outspoken.scan.GazeSettings(irisDownStrength = 0.017f))
        val c = ConversationController(speak = { said += it }, gaze = stepper).apply { moveByEyes = true }
        var t = 10_000L
        fun frame(open: Float, iris: Float) {
            c.onSample(EyeSample(t, true, open, open, gaze = Dot(0f, 0.5f), irisY = iris))
            t += 33
        }
        repeat(20) { frame(0.95f, -0.03f) }
        // Phone log 00:46:18: eyes shut read an iris drop of +0.039 from rest.
        repeat(16) { frame(0.05f, 0.009f) }
        repeat(4) { frame(0.95f, -0.03f) }
        assertEquals(listOf("I need water"), said)
        assertEquals(0, c.ui.value.highlighted.coerceAtLeast(0))
    }

    private fun wink(left: Float, right: Float) {
        val end = time + 500
        while (time < end) {
            controller.onSample(EyeSample(time, true, left, right, gaze = Dot(0f, 0f)))
            time += 33
        }
        frames(800)
    }

    @Test
    fun `a left wink moves down and a right wink moves up`() {
        frames(500)
        wink(left = 0.05f, right = 0.95f)
        assertEquals(1, highlighted)
        wink(left = 0.05f, right = 0.95f)
        assertEquals(2, highlighted)
        wink(left = 0.95f, right = 0.05f)
        assertEquals(1, highlighted)
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun `after winking, closing both eyes chooses`() {
        frames(500)
        wink(left = 0.05f, right = 0.95f)
        blink()
        assertEquals(listOf("I am in pain"), said)
    }

    @Test
    fun `a wink moves exactly once even when it shifts the gaze`() {
        frames(500)
        // Closing one eye pushes the gaze reading down as well.
        val end = time + 500
        while (time < end) {
            controller.onSample(EyeSample(time, true, 0.05f, 0.95f, gaze = Dot(0f, 0.6f)))
            time += 33
        }
        frames(800)
        assertEquals(1, highlighted)
    }
}
