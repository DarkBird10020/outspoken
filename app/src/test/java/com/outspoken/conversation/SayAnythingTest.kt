package com.outspoken.conversation

import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Say anything": the sentence builder inside the conversation, picked by tap or by eyes. */
class SayAnythingTest {

    private val said = mutableListOf<String>()
    private val wordRequests = mutableListOf<Pair<Int, String>>()
    private val controller = ConversationController(
        speak = { said += it },
        requestWords = { id, _, sentence ->
            wordRequests += id to sentence
            true
        },
    )
    private var time = 10_000L

    private val ui get() = controller.ui.value
    private val builder get() = ui.builder

    private fun tap(card: Int) = controller.onTap(card, time)

    private fun open() {
        tap(Board.SAY_ANYTHING)
        controller.onTick(time)
    }

    @Test
    fun `the Say anything card opens the builder with built-in words and asks the model`() {
        open()
        assertNotNull(builder)
        assertEquals(listOf("I", "Please", "My", "Can you"), builder!!.words)
        assertEquals(listOf(1 to ""), wordRequests)
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun `picking words builds the sentence and asks for the next words each time`() {
        open()
        tap(0)
        tap(0)
        controller.onTick(time)
        assertEquals("I want", builder!!.sentence)
        assertEquals(listOf("", "I", "I want"), wordRequests.map { it.second })
    }

    @Test
    fun `the model's words replace the built-in ones, and only for the latest request`() {
        open()
        tap(0)
        controller.onWords(wordRequests.first().first, listOf("stale"), "Stale sentence", time)
        assertEquals("want", builder!!.words[0])
        controller.onWords(wordRequests.last().first, listOf("miss", "am", "need", "feel"), "I miss my family", time)
        assertEquals(listOf("miss", "am", "need", "feel"), builder!!.words)
        assertEquals("I miss my family", builder!!.completion)
    }

    @Test
    fun `Speak says the sentence, adds it to the conversation and the frequent phrases, and closes`() {
        open()
        tap(0)
        tap(0)
        tap(SentenceBuilder.SPEAK)
        assertEquals(listOf("I want"), said)
        assertEquals(listOf("I want"), controller.history)
        assertTrue("I want" in controller.frequentPhrases)
        assertNull(builder)
    }

    @Test
    fun `Finish it for me says the model's sentence`() {
        open()
        tap(0)
        controller.onWords(wordRequests.last().first, emptyList(), "I am cold", time)
        tap(SentenceBuilder.FINISH)
        assertEquals(listOf("I am cold"), said)
        assertNull(builder)
    }

    @Test
    fun `Delete removes the last word, and on an empty sentence it exits without speaking`() {
        open()
        tap(0)
        tap(SentenceBuilder.DELETE)
        controller.onTick(time)
        assertEquals("", builder!!.sentence)
        tap(SentenceBuilder.DELETE)
        controller.onTick(time)
        assertNull(builder)
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun `the back button closes without speaking and late words are ignored`() {
        open()
        tap(0)
        controller.closeSayAnything(time)
        controller.onWords(wordRequests.last().first, listOf("want"), "I want water", time)
        assertNull(builder)
        assertEquals(emptyList<String>(), said)
    }

    @Test
    fun `More words shows the next four`() {
        open()
        tap(SentenceBuilder.MORE_WORDS)
        controller.onTick(time)
        assertEquals(listOf("I need", "I want", "I feel", "Thank you"), builder!!.words)
    }

    @Test
    fun `by eyes a look up moves through the builder's cards and a blink picks the lit word`() {
        controller.moveByEyes = true
        controller.upMovesNext = true
        fun frames(ms: Long, open: Float = 0.95f, gazeY: Float = 0f) {
            val end = time + ms
            while (time < end) {
                controller.onSample(EyeSample(time, true, open, open, gaze = Dot(0f, gazeY)))
                time += 33
            }
        }
        frames(500)
        open()
        frames(500, gazeY = -0.7f)
        frames(1_300)
        assertEquals(1, ui.highlighted)
        frames(500, open = 0.05f)
        frames(200)
        assertEquals("Please", builder!!.sentence)
    }
}
