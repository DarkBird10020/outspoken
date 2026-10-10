package com.outspoken.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageVotesTest {

    @Test
    fun `guesses that agree name the language, whatever the region`() {
        val votes = LanguageVotes()
        repeat(30) { votes.add("en-in") }
        votes.add("EN_US")
        assertEquals("en", votes.dominant())
    }

    @Test
    fun `too few guesses name nothing`() {
        val votes = LanguageVotes()
        repeat(4) { votes.add("en-in") }
        assertNull(votes.dominant())
    }

    @Test
    fun `mixed guesses name nothing`() {
        val votes = LanguageVotes()
        repeat(6) { votes.add("en-in") }
        repeat(4) { votes.add("hi-in") }
        assertNull(votes.dominant())
    }

    @Test
    fun `clear starts the next sentence afresh`() {
        val votes = LanguageVotes()
        repeat(10) { votes.add("hi-in") }
        votes.clear()
        repeat(10) { votes.add("en-in") }
        assertEquals("en", votes.dominant())
        assertEquals("en 10", votes.describe())
    }



    @Test
    fun `mostly devanagari tells the scripts apart`() {
        assertTrue(mostlyDevanagari("व्हाट'स योर नेम"))
        assertFalse(mostlyDevanagari("What's your name"))
        assertFalse(mostlyDevanagari("123 ?"))
    }
}
