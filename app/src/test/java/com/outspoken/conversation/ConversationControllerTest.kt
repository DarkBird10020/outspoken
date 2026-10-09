package com.outspoken.conversation

import com.outspoken.eye.EyeSample
import com.outspoken.suggest.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationControllerTest {

    private val said = mutableListOf<String>()
    private val requests = mutableListOf<Pair<Int, List<Turn>>>()
    private var modelThere = false
    private var cues = 0
    private val controller = ConversationController(
        speak = { said += it },
        requestReplies = { id, turns ->
            requests += id to turns
            modelThere
        },
        cue = { cues++ },
    )
    private var time = 10_000L

    private fun frames(ms: Long, open: Float, faceFound: Boolean = true) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, faceFound, open, open))
            time += 33
        }
    }

    private fun shortBlink() {
        frames(700, open = 0.05f)
        frames(200, open = 0.95f)
    }

    private fun longBlink() {
        frames(1_300, open = 0.05f)
        frames(200, open = 0.95f)
    }

    private val highlighted get() = controller.ui.value.highlighted

    @Test
    fun `long blink on the first card says I need water`() {
        frames(500, open = 0.95f)
        longBlink()
        assertEquals(listOf("I need water"), said)
        assertEquals(listOf(Turn(fromListener = false, text = "I need water")), controller.history)
    }

    @Test
    fun `the highlight waits for the person`() {
        frames(10_000, open = 0.95f)
        assertEquals(0, highlighted)
    }

    @Test
    fun `short blinks move to the next card and wrap round`() {
        frames(500, open = 0.95f)
        shortBlink()
        assertEquals(1, highlighted)
        repeat(4) { shortBlink() }
        assertEquals(Board.YES_NO, highlighted)
        shortBlink()
        assertEquals(0, highlighted)
        assertTrue(said.isEmpty())
    }

    @Test
    fun `step then choose says the second reply`() {
        frames(500, open = 0.95f)
        shortBlink()
        longBlink()
        assertEquals(listOf("I am in pain"), said)
    }

    @Test
    fun `a cue sounds while the eyes are still shut long enough to choose`() {
        frames(500, open = 0.95f)
        frames(1_100, open = 0.05f)
        assertEquals(1, cues)
        frames(200, open = 0.95f)
        assertEquals(1, cues)
    }

    @Test
    fun `normal blinks do nothing`() {
        frames(500, open = 0.95f)
        repeat(5) {
            frames(250, open = 0.05f)
            frames(800, open = 0.95f)
        }
        assertTrue(said.isEmpty())
        assertEquals(0, highlighted)
    }

    @Test
    fun `blinks are ignored while speaking`() {
        frames(500, open = 0.95f)
        longBlink()
        longBlink()
        shortBlink()
        assertEquals(1, said.size)
        assertEquals(-1, highlighted)
    }

    @Test
    fun `after speaking the highlight is back on the first card`() {
        frames(500, open = 0.95f)
        shortBlink()
        longBlink()
        controller.onSpeechDone(time)
        assertEquals(0, highlighted)
        longBlink()
        assertEquals(listOf("I am in pain", "I need water"), said)
    }

    @Test
    fun `losing the face keeps the highlight where it was`() {
        frames(500, open = 0.95f)
        shortBlink()
        frames(2_000, open = 0.95f, faceFound = false)
        assertFalse(controller.ui.value.faceFound)
        assertEquals(1, highlighted)
    }

    @Test
    fun `a blink that began before the cards changed is ignored`() {
        frames(500, open = 0.95f)
        frames(600, open = 0.05f)
        controller.onTap(Board.YES_NO, time)
        frames(700, open = 0.05f)
        frames(200, open = 0.95f)
        assertTrue(said.isEmpty())
        assertEquals(0, highlighted)
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
        frames(500, open = 0.95f)
        longBlink()
        assertEquals(listOf(Turn(false, "I need water")), requests.single().second)
    }

    @Test
    fun `model replies replace the cards and the highlight goes to the top`() {
        modelThere = true
        frames(500, open = 0.95f)
        longBlink()
        controller.onSpeechDone(time)
        val replies = listOf("Cold water please", "Just a sip", "With a straw", "Thank you")
        controller.onReplies(requests.last().first, replies, fromModel = true, nowMs = time)
        assertEquals(replies, controller.ui.value.replies)
        assertEquals(0, highlighted)
        longBlink()
        assertEquals("Cold water please", said.last())
    }

    @Test
    fun `after speaking the board waits for the model`() {
        modelThere = true
        frames(500, open = 0.95f)
        longBlink()
        controller.onSpeechDone(time)
        frames(1_000, open = 0.95f)
        assertEquals(-1, highlighted)
        controller.onReplies(requests.last().first, listOf("A one", "B two", "C three", "D four"), fromModel = true, nowMs = time)
        assertEquals(0, highlighted)
    }

    @Test
    fun `a slow model does not hold the board forever`() {
        modelThere = true
        frames(500, open = 0.95f)
        longBlink()
        controller.onSpeechDone(time)
        frames(3_000, open = 0.95f)
        assertEquals(0, highlighted)
        assertEquals("I need water", controller.ui.value.replies.first())
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
    fun `speech that never reports done does not freeze the board`() {
        frames(500, open = 0.95f)
        longBlink()
        frames(11_000, open = 0.95f)
        assertEquals(0, highlighted)
    }
}
