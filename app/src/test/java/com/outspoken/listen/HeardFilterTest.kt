package com.outspoken.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeardFilterTest {

    private val filter = HeardFilter(echoWindowMs = 1_500)

    @Test
    fun `a question is cleaned and kept`() {
        assertEquals("Are you in pain?", filter.accept("  are   you in pain? ", 1_000))
    }

    @Test
    fun `blank and one letter results are dropped`() {
        assertNull(filter.accept("   ", 1_000))
        assertNull(filter.accept("a", 1_000))
    }

    @Test
    fun `nothing is kept while the phone speaks`() {
        filter.onSpeechStart("I need water")
        assertNull(filter.accept("Are you thirsty?", 1_000))
    }

    @Test
    fun `the tail of the phone's own voice is dropped`() {
        filter.onSpeechStart("I need water")
        filter.onSpeechDone(10_000)
        assertNull(filter.accept("Where are you?", 11_000))
        assertEquals("Where are you?", filter.accept("Where are you?", 11_600))
    }

    @Test
    fun `what the phone just said is not taken as a question`() {
        filter.onSpeechStart("Look at the screen")
        filter.onSpeechDone(10_000)
        assertNull(filter.accept("look at the screen.", 20_000))
        assertEquals("Look at me", filter.accept("look at me", 20_000))
    }

    @Test
    fun `quick topics are all questions`() {
        assertTrue(QuickTopics.all.all { (label, question) -> label.isNotBlank() && question.endsWith("?") })
    }
}
