package com.outspoken.conversation

import com.outspoken.eye.EyeSample
import com.outspoken.scan.Scanner
import com.outspoken.suggest.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationControllerTest {

    private val said = mutableListOf<String>()
    private val requests = mutableListOf<Pair<Int, List<Turn>>>()
    private var modelThere = false
    private val controller = ConversationController(
        speak = { said += it },
        requestReplies = { id, turns ->
            requests += id to turns
            modelThere
        },
        scanner = Scanner(intervalMs = 1_200),
    )
    private var time = 10_000L

    private fun frames(ms: Long, open: Float, faceFound: Boolean = true) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, faceFound, open, open))
            time += 33
        }
    }

    private fun blink() {
        frames(700, open = 0.05f)
        frames(100, open = 0.95f)
    }

    @Test
    fun `blinking on the first card says I need water`() {
        frames(100, open = 0.95f)
        blink()
        assertEquals(listOf("I need water"), said)
        assertEquals(listOf(Turn(fromListener = false, text = "I need water")), controller.history)
    }

    @Test
    fun `the card highlighted when the eyes shut is chosen`() {
        frames(900, open = 0.95f)
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
    fun `speaking asks for new replies with the conversation so far`() {
        frames(100, open = 0.95f)
        blink()
        assertEquals(listOf(Turn(false, "I need water")), requests.single().second)
    }

    @Test
    fun `model replies replace the cards and scanning starts at the top`() {
        modelThere = true
        frames(100, open = 0.95f)
        blink()
        controller.onSpeechDone(time)
        frames(1_300, open = 0.95f)
        val replies = listOf("Cold water please", "Just a sip", "With a straw", "Thank you")
        controller.onReplies(requests.last().first, replies, fromModel = true, nowMs = time)
        assertEquals(replies, controller.ui.value.replies)
        assertEquals(0, controller.ui.value.highlighted)
        blink()
        assertEquals("Cold water please", said.last())
    }

    @Test
    fun `an old answer is ignored`() {
        controller.refreshReplies()
        controller.refreshReplies()
        controller.onReplies(requests.first().first, listOf("A", "B", "C", "D"), fromModel = true, nowMs = time)
        assertEquals("I need water", controller.ui.value.replies.first())
    }

    @Test
    fun `a fallback answer keeps the phrase bank`() {
        controller.refreshReplies()
        controller.onReplies(requests.last().first, listOf("x", "y", "z", "w"), fromModel = false, nowMs = time)
        assertEquals("I need water", controller.ui.value.replies.first())
    }

    @Test
    fun `after speaking, scanning waits for the model`() {
        modelThere = true
        frames(100, open = 0.95f)
        blink()
        controller.onSpeechDone(time)
        frames(1_000, open = 0.95f)
        assertEquals(-1, controller.ui.value.highlighted)
        controller.onReplies(requests.last().first, listOf("A one", "B two", "C three", "D four"), fromModel = true, nowMs = time)
        assertEquals(0, controller.ui.value.highlighted)
    }

    @Test
    fun `a slow model does not hold scanning forever`() {
        modelThere = true
        frames(100, open = 0.95f)
        blink()
        controller.onSpeechDone(time)
        frames(3_000, open = 0.95f)
        assertTrue(controller.ui.value.highlighted >= 0)
        assertEquals("I need water", controller.ui.value.replies.first())
    }

    @Test
    fun `speech that never reports done does not freeze the board`() {
        frames(100, open = 0.95f)
        blink()
        frames(11_000, open = 0.95f)
        assertTrue(controller.ui.value.highlighted >= 0)
    }

    @Test
    fun `short normal blinks pick nothing`() {
        frames(100, open = 0.95f)
        repeat(5) {
            frames(250, open = 0.05f)
            frames(800, open = 0.95f)
        }
        assertTrue(said.isEmpty())
    }
}
