package com.outspoken.conversation

import com.outspoken.eye.EyeSample
import com.outspoken.scan.Scanner
import com.outspoken.suggest.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How model replies reach the cards: asked for after each sentence, waited for, never stale. */
class ConversationRepliesTest {

    private val said = mutableListOf<String>()
    private val requests = mutableListOf<Pair<Int, List<Turn>>>()
    private var modelThere = true
    private val controller = ConversationController(
        speak = { said += it },
        scanner = Scanner(intervalMs = 1_200),
        requestReplies = { id, turns ->
            requests += id to turns
            modelThere
        },
    )
    private var time = 10_000L
    private val ui get() = controller.ui.value

    private fun frames(ms: Long, open: Float = 0.95f) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, true, open, open))
            time += 33
        }
    }

    private fun blink() {
        frames(500, open = 0.05f)
        frames(100)
    }

    private fun sayFirstCard() {
        frames(100)
        blink()
    }

    @Test
    fun `speaking asks for new replies with the conversation so far`() {
        sayFirstCard()
        assertEquals(listOf(Turn(false, "I need water")), requests.single().second)
    }

    @Test
    fun `the phrase bank shows while the model thinks`() {
        sayFirstCard()
        assertEquals("I need water", ui.replies.first())
    }

    @Test
    fun `after speaking the highlight waits for the model`() {
        sayFirstCard()
        controller.onSpeechDone(time)
        frames(1_000)
        assertEquals(-1, ui.highlighted)
        val replies = listOf("Cold water please", "Just a sip", "With a straw", "Thank you")
        controller.onReplies(requests.last().first, replies, fromModel = true, timeMs = time)
        assertEquals(replies, ui.replies)
        assertEquals(0, ui.highlighted)
    }

    @Test
    fun `a model reply is chosen with a blink`() {
        sayFirstCard()
        controller.onSpeechDone(time)
        controller.onReplies(requests.last().first, listOf("Cold water please", "Just a sip", "With a straw", "Thank you"), fromModel = true, timeMs = time)
        blink()
        assertEquals("Cold water please", said.last())
    }

    @Test
    fun `blinks are ignored while waiting for the model`() {
        sayFirstCard()
        controller.onSpeechDone(time)
        blink()
        assertEquals(1, said.size)
    }

    @Test
    fun `a slow model does not hold the board forever`() {
        sayFirstCard()
        controller.onSpeechDone(time)
        frames(3_000)
        assertTrue(ui.highlighted >= 0)
        assertEquals("I need water", ui.replies.first())
    }

    @Test
    fun `with no model the board carries on at once`() {
        modelThere = false
        sayFirstCard()
        controller.onSpeechDone(time)
        assertEquals(0, ui.highlighted)
    }

    @Test
    fun `an old answer is ignored`() {
        controller.refreshReplies()
        controller.refreshReplies()
        controller.onReplies(requests.first().first, listOf("A", "B", "C", "D"), fromModel = true, timeMs = time)
        assertEquals("I need water", ui.replies.first())
    }

    @Test
    fun `a fallback answer keeps the phrase bank`() {
        controller.refreshReplies()
        controller.onReplies(requests.last().first, listOf("x", "y", "z", "w"), fromModel = false, timeMs = time)
        assertEquals("I need water", ui.replies.first())
    }

    @Test
    fun `speech that never reports done does not freeze the board`() {
        modelThere = false
        sayFirstCard()
        frames(11_000)
        assertTrue(ui.highlighted >= 0)
    }
}
