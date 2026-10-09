package com.outspoken.conversation

import com.outspoken.eye.EyeSample
import com.outspoken.scan.Scanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationControllerTest {

    private val said = mutableListOf<String>()
    private val controller = ConversationController(speak = { said += it }, scanner = Scanner(intervalMs = 1_200))
    private var time = 10_000L

    private fun frames(ms: Long, open: Float, faceFound: Boolean = true) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, faceFound, open, open))
            time += 33
        }
    }

    private fun blink() {
        frames(500, open = 0.05f)
        frames(100, open = 0.95f)
    }

    @Test
    fun `blinking on the first card says I need water`() {
        frames(100, open = 0.95f)
        blink()
        assertEquals(listOf("I need water"), said)
        assertEquals(listOf("I need water"), controller.history)
    }

    @Test
    fun `the card highlighted when the eyes shut is chosen`() {
        frames(1_000, open = 0.95f)
        // Eyes shut on card 0; the highlight moves to card 1 while they are closed.
        blink()
        assertEquals(listOf("I need water"), said)
    }

    @Test
    fun `waiting for the second card says the second reply`() {
        frames(1_300, open = 0.95f)
        blink()
        assertEquals(listOf("I am in pain"), said)
    }

    @Test
    fun `blinks are ignored while speaking`() {
        frames(100, open = 0.95f)
        blink()
        blink()
        assertEquals(1, said.size)
        assertEquals(-1, controller.ui.value.highlighted)
    }

    @Test
    fun `scanning starts again from the first card after speaking`() {
        frames(100, open = 0.95f)
        blink()
        controller.onSpeechDone(time)
        assertEquals(0, controller.ui.value.highlighted)
        blink()
        assertEquals(listOf("I need water", "I need water"), said)
    }

    @Test
    fun `losing the face pauses the scanner`() {
        frames(100, open = 0.95f)
        assertTrue(controller.ui.value.faceFound)
        frames(5_000, open = 0.95f, faceFound = false)
        assertFalse(controller.ui.value.faceFound)
        assertEquals(0, controller.ui.value.highlighted)
    }

    @Test
    fun `tapping a card speaks it`() {
        controller.onTap(3, time)
        assertEquals(listOf("I need the toilet"), said)
    }

    @Test
    fun `tapping yes no changes the cards without speaking`() {
        controller.onTap(Board.YES_NO, time)
        assertEquals(listOf("Yes", "No"), controller.ui.value.replies)
        assertTrue(said.isEmpty())
    }

    @Test
    fun `a late camera frame does not move the highlight back`() {
        frames(100, open = 0.95f)
        controller.onTick(10_000 + 1_210)
        assertEquals(1, controller.ui.value.highlighted)
        // Detection takes about 30 ms, so this frame is stamped before the tick above.
        controller.onSample(EyeSample(10_000 + 1_180, true, 0.95f, 0.95f))
        assertEquals(1, controller.ui.value.highlighted)
    }

    @Test
    fun `allTurns preserves speaker and listener turns in order`() {
        controller.onHeard("How are you feeling?", time)
        controller.onTap(0, time + 100)
        assertEquals(2, controller.allTurns.size)
        assertTrue(controller.allTurns[0].fromListener)
        assertEquals("How are you feeling?", controller.allTurns[0].text)
        assertFalse(controller.allTurns[1].fromListener)
        assertEquals("I need water", controller.allTurns[1].text)
    }

    @Test
    fun `frequent phrases sorts by count descending`() {
        controller.onTap(0, time)
        controller.onSpeechDone(time + 100)
        controller.onTap(1, time + 200)
        controller.onSpeechDone(time + 300)
        controller.onTap(1, time + 400)
        assertEquals(listOf("I am in pain", "I need water"), controller.frequentPhrases)
    }
}
