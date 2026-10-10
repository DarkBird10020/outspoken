package com.outspoken.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPromptTest {

    @Test
    fun `the prompt gives the words so far and asks for 4 sentences that start with them`() {
        val prompt = buildWordPrompt(WordRequest(listOf(Turn(true, "Are you hungry")), "I want"))
        assertTrue(prompt.contains("Words so far: \"I want\""))
        assertTrue(prompt.contains("Visitor: Are you hungry"))
        assertTrue(prompt.contains("starts with exactly the words so far"))
        assertTrue(prompt.contains("JSON list of 4 strings"))
        assertTrue(prompt.endsWith("Answer:"))
    }

    @Test
    fun `an empty sentence lets the sentences start any way`() {
        assertTrue(buildWordPrompt(WordRequest(emptyList(), "")).contains("No words yet"))
    }

    @Test
    fun `next words are cut from whole sentences, so they read on from the sentence so far`() {
        // Phone, 10:18:13: asked for loose next words after "I", the model gave "a little tired",
        // and the person got "I a little tired". Its whole sentence was "I am feeling a little tired now."
        val answer = parseWordAnswer(
            """["I am feeling a little tired now.", "I need some water", "I want to sleep", "I feel pain in my back"]""",
            "I",
        )
        assertEquals("I am feeling a little tired now.", answer.completion)
        assertEquals(listOf("am feeling", "need some", "want to", "feel pain"), answer.words)
        assertEquals(4, answer.sentences.size)
    }

    @Test
    fun `a sentence must start with the words so far word for word`() {
        // "It is cold" starts with the letter "I" but not with the word "I".
        val answer = parseWordAnswer("""["It is cold here", "Please bring water", "I am cold"]""", "I")
        assertEquals("I am cold", answer.completion)
        assertEquals(listOf("am cold"), answer.words)
        assertEquals(listOf("It is cold here", "Please bring water"), answer.unfit)
    }

    @Test
    fun `a next word stops at a comma or full stop`() {
        val answer = parseWordAnswer("""["No, I am fine", "Yes. Thank you", "Please call the nurse"]""", "")
        assertEquals("No, I am fine", answer.completion)
        assertEquals(listOf("No", "Yes", "Please call"), answer.words)
    }

    @Test
    fun `after A little the cards read on instead of repeating it`() {
        // Phone, 08:39:38: "A little" was offered "a little water", giving "A little a little water".
        val answer = parseWordAnswer("""["A little more rest now", "A little water, please", "A little help here"]""", "A little")
        assertEquals(listOf("more rest", "water", "help here"), answer.words)
    }

    @Test
    fun `repeated sentences and a word that only repeats the last one are dropped`() {
        val answer = parseWordAnswer("""["I want water", "I want Water.", "I want want", "I want to sleep"]""", "I want")
        assertEquals("I want water", answer.completion)
        assertEquals(listOf("water", "to sleep"), answer.words)
    }

    @Test
    fun `the sentence so far alone, or a sentence too long to say, is not used`() {
        val answer = parseWordAnswer(
            """["I want", "I want to go home and then sleep for a very long time today now", "I want tea"]""",
            "I want",
        )
        assertEquals("I want tea", answer.completion)
        assertEquals(listOf("tea"), answer.words)
    }

    @Test
    fun `text around the list and code fences are tolerated`() {
        val answer = parseWordAnswer("Sure!\n```json\n[\"I am cold\", \"I am tired\"]\n```", "I am")
        assertEquals("I am cold", answer.completion)
        assertEquals(listOf("cold", "tired"), answer.words)
    }

    @Test
    fun `no list at all gives nothing, so the built-in words stay`() {
        val answer = parseWordAnswer("I cannot help with that.", "I")
        assertNull(answer.completion)
        assertEquals(emptyList<String>(), answer.words)
    }
}
