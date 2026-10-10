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
    fun `english written in hindi letters reads back in latin letters`() {
        // From the phone, 19:18 and 19:20: English questions heard while listening in Hindi.
        assertEquals("vhaat's yor nem", romanise("व्हाट'स योर नेम"))
        assertEquals("he vhaat yor nem", romanise("हे व्हाट योर नेम"))
        assertEquals("feeling", romanise("फीलिंग"))
    }

    @Test
    fun `latin letters and punctuation pass through`() {
        assertEquals("OK, 2 times?", romanise("OK, 2 times?"))
    }

    @Test
    fun `mostly devanagari tells the scripts apart`() {
        assertTrue(mostlyDevanagari("व्हाट'स योर नेम"))
        assertFalse(mostlyDevanagari("What's your name"))
        assertFalse(mostlyDevanagari("123 ?"))
    }
}
