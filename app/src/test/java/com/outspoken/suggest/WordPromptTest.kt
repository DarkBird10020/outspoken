package com.outspoken.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPromptTest {

    @Test
    fun `the prompt gives the sentence so far and asks for a list of 5`() {
        val prompt = buildWordPrompt(WordRequest(listOf(Turn(true, "Are you hungry")), "I want"))
        assertTrue(prompt.contains("Sentence so far: \"I want\""))
        assertTrue(prompt.contains("Visitor: Are you hungry"))
        assertTrue(prompt.contains("JSON list of 5 strings"))
        assertTrue(prompt.endsWith("Answer:"))
    }

    @Test
    fun `an empty sentence asks for first words`() {
        assertTrue(buildWordPrompt(WordRequest(emptyList(), "")).contains("good first words"))
    }

    @Test
    fun `a clean answer gives the finished sentence and four words`() {
        val answer = parseWordAnswer("""["I want to go outside for a while", "outside", "home", "to the", "back"]""", "I want to go")
        assertEquals("I want to go outside for a while", answer.completion)
        assertEquals(listOf("outside", "home", "to the", "back"), answer.words)
    }

    @Test
    fun `a finished sentence that does not start with the sentence so far is dropped`() {
        val answer = parseWordAnswer("""["Please bring water", "to", "some", "water", "my"]""", "I want")
        assertNull(answer.completion)
        assertEquals(listOf("to", "some", "water", "my"), answer.words)
    }

    @Test
    fun `long words, repeats and the word just picked are dropped`() {
        val answer = parseWordAnswer("""["I want water now please", "want", "a glass of cold water", "water", "Water", "to"]""", "I want")
        assertEquals("I want water now please", answer.completion)
        assertEquals(listOf("water", "to"), answer.words)
    }

    @Test
    fun `text around the list and code fences are tolerated`() {
        val answer = parseWordAnswer("Sure!\n```json\n[\"I am cold\", \"cold\", \"tired\"]\n```", "I am")
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
