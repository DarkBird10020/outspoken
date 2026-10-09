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
    fun `parts of the day`() {
        assertEquals(listOf("night", "morning", "afternoon", "evening", "night"), listOf(4, 5, 12, 17, 21).map(::partOfDay))
    }
}
