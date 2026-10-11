package com.outspoken.suggest

import com.outspoken.profile.PatientProfile
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
        assertFalse(prompt.contains("a question back"))
    }

    @Test
    fun `earlier cards are not put in the prompt`() {
        // Listing them made the model copy them back five times in a row (04:53 to 04:54 run).
        val turns = listOf(Turn(false, "Need water, yes?"), Turn(true, "In pain"))
        val prompt = buildPrompt(SuggestionRequest(turns, hourOfDay = 4))
        assertFalse(prompt.contains("Offered"))
        assertTrue(prompt.contains("Visitor: In pain\n"))
        assertTrue(prompt.endsWith("Answer:"))
    }

    @Test
    fun `a yes or no question just asked decides the replies`() {
        // Phone 05:27:38: "Are you in pain" got rest and water cards, none about pain.
        val turns = listOf(Turn(false, "Yes, I need a nap."), Turn(true, "Are you in pain"))
        val prompt = buildPrompt(SuggestionRequest(turns, hourOfDay = 5))
        assertTrue(
            prompt.endsWith(
                "The Visitor just said: \"Are you in pain\". All 4 replies must reply to that directly. " +
                    "Give a yes, a no, and two more specific answers.\nAnswer:",
            ),
        )
        assertFalse(prompt.contains("Mix the kinds"))
    }

    @Test
    fun `a greeting is replied to without asking for yes or no`() {
        val prompt = buildPrompt(SuggestionRequest(listOf(Turn(true, "Hello")), hourOfDay = 9))
        assertTrue(prompt.contains("The Visitor just said: \"Hello\". All 4 replies must reply to that directly.\n"))
        assertFalse(prompt.contains("Give a yes"))
    }

    @Test
    fun `after the person speaks the replies mix needs`() {
        val turns = listOf(Turn(true, "Are you in pain"), Turn(false, "Yes, my back hurts"))
        val prompt = buildPrompt(SuggestionRequest(turns, hourOfDay = 9))
        assertTrue(prompt.contains("Mix the kinds"))
        assertFalse(prompt.contains("The Visitor just said"))
    }

    @Test
    fun `yes or no questions are told apart from the first word`() {
        assertTrue(isYesNoQuestion("Are you in pain"))
        assertTrue(isYesNoQuestion("Hey you need anything"))
        assertTrue(isYesNoQuestion("Do you want to eat in the breakfast"))
        assertFalse(isYesNoQuestion("What would you like for breakfast"))
        assertFalse(isYesNoQuestion("Hello"))
        assertFalse(isYesNoQuestion(""))
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

    @Test
    fun `the profile goes into the replies prompt before the conversation, and is left out when empty`() {
        val turns = listOf(Turn(true, "Who should I call?"))
        val withProfile = buildPrompt(SuggestionRequest(turns, 10, profile = PatientProfile.SAMPLE))
        assertTrue(withProfile.contains("People close to them: wife Meera"))
        assertTrue(withProfile.indexOf("About the Person") < withProfile.indexOf("Conversation so far"))
        assertFalse(buildPrompt(SuggestionRequest(turns, 10)).contains("About the Person"))
        assertFalse(buildPrompt(SuggestionRequest(turns, 10, profile = PatientProfile())).contains("About the Person"))
    }
}
