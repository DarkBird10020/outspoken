package com.outspoken.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {

    @Test
    fun `asks for four replies as a JSON list`() {
        val prompt = buildPrompt(SuggestionRequest(emptyList(), hourOfDay = 9))
        assertTrue(prompt.contains("4 different replies"))
        assertTrue(prompt.contains("JSON list of 4 strings"))
        assertTrue(prompt.contains("at most 8 words"))
    }

    @Test
    fun `empty conversation uses the time of day`() {
        val prompt = buildPrompt(SuggestionRequest(emptyList(), hourOfDay = 21))
        assertTrue(prompt.contains("It is night."))
        assertTrue(prompt.contains("Nothing has been said yet."))
    }

    @Test
    fun `conversation lines name who spoke`() {
        val turns = listOf(Turn(true, "Are you in pain?"), Turn(false, "I am in pain"))
        val prompt = buildPrompt(SuggestionRequest(turns, hourOfDay = 14))
        assertTrue(prompt.contains("Visitor: Are you in pain?\nPerson: I am in pain"))
    }

    @Test
    fun `only recent turns are sent`() {
        val turns = (1..10).map { Turn(false, "Line $it") }
        val prompt = buildPrompt(SuggestionRequest(turns, hourOfDay = 14))
        assertFalse(prompt.contains("Line 4\n"))
        assertTrue(prompt.contains("Line 5"))
        assertTrue(prompt.contains("Line 10"))
    }

    @Test
    fun `says who is speaking and that replies are never the visitor's`() {
        val prompt = buildPrompt(SuggestionRequest(emptyList(), hourOfDay = 9))
        assertTrue(prompt.contains("a person in bed who cannot move or speak"))
        assertTrue(prompt.contains("said by the Person, never by the Visitor"))
        assertTrue(prompt.contains("a need or feeling such as pain, thirst or rest"))
    }

    @Test
    fun `replies offered and passed over are listed, not the one picked`() {
        val turns = listOf(Turn(false, "I am in pain"))
        val offered = listOf("I am in pain", "What is happening now?", "I need rest")
        val prompt = buildPrompt(SuggestionRequest(turns, hourOfDay = 14, offered = offered))
        assertTrue(prompt.contains("Offered just before and not picked, so try others: What is happening now?; I need rest\n"))
        assertTrue(prompt.endsWith("Answer:"))
    }

    @Test
    fun `nothing offered before adds no line`() {
        assertFalse(buildPrompt(SuggestionRequest(emptyList(), hourOfDay = 14)).contains("Offered just before"))
    }

    @Test
    fun `parts of the day`() {
        assertEquals(listOf("night", "morning", "afternoon", "evening", "night"), listOf(4, 5, 12, 17, 21).map(::partOfDay))
    }

    @Test
    fun `includes one worked example and ends asking for the answer`() {
        val prompt = buildPrompt(SuggestionRequest(emptyList(), hourOfDay = 9))
        assertTrue(prompt.contains(EXAMPLE_ANSWER))
        assertTrue(prompt.endsWith("Answer:"))
    }
}
