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
    fun `a short sentence with one sure guess names that language`() {
        // 06:32:12 on the phone: "language heard: hi-in, fairly sure", then "Tumhare kya Halchal hai".
        val votes = LanguageVotes()
        votes.addSure("hi-IN")
        repeat(2) { votes.add("en-in") }
        assertEquals("hi", votes.dominant())
        // Enough guesses that agree still decide.
        repeat(10) { votes.add("en-in") }
        assertEquals("en", votes.dominant())
        votes.clear()
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
