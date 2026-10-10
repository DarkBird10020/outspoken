package com.outspoken.conversation

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkSettings
import com.outspoken.eye.EyeSample
import com.outspoken.scan.Scanner
import com.outspoken.stats.BlinkTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Blink accuracy counted live in the conversation: closes made to choose, and how many chose. */
class ConversationAccuracyTest {

    private val said = mutableListOf<String>()
    private val controller = ConversationController(
        speak = { said += it },
        detector = BlinkDetector(BlinkSettings(minBlinkMs = 400, maxBlinkMs = 1_500)),
        scanner = Scanner(intervalMs = 1_200),
    )
    private var time = 10_000L

    private fun frames(ms: Long, open: Float, faceFound: Boolean = true) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, faceFound, open.takeIf { faceFound }, open.takeIf { faceFound }))
            time += 33
        }
    }

    private fun close(ms: Long) {
        frames(300, open = 0.95f)
        frames(ms, open = 0.05f)
        frames(300, open = 0.95f)
    }

    @Test
    fun `a close that chooses is caught`() {
        close(600)
        assertEquals(BlinkTally(caught = 1, missed = 0), controller.blinks)
        assertEquals(1, said.size)
    }

    @Test
    fun `a close too long to choose is a miss`() {
        close(1_700)
        assertEquals(BlinkTally(caught = 0, missed = 1), controller.blinks)
        assertTrue(said.isEmpty())
    }

    @Test
    fun `normal blinks are not tries`() {
        close(200)
        assertEquals(BlinkTally(), controller.blinks)
    }

    @Test
    fun `closes while the phone speaks are not tries`() {
        close(600)
        close(600)
        assertEquals(BlinkTally(caught = 1, missed = 0), controller.blinks)
        assertEquals(1, said.size)
    }

    @Test
    fun `a help call is not a try`() {
        frames(300, open = 0.95f)
        frames(2_300, open = 0.05f)
        frames(500, open = 0.95f)
        frames(600, open = 0.05f)
        frames(300, open = 0.95f)
        assertEquals(BlinkTally(), controller.blinks)
    }

    @Test
    fun `a close that ends with no face is not a try`() {
        frames(300, open = 0.95f)
        frames(600, open = 0.05f)
        frames(600, open = 0f, faceFound = false)
        frames(300, open = 0.95f)
        assertEquals(BlinkTally(), controller.blinks)
    }
}
