package com.outspoken.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientProfileTest {

    @Test
    fun `a saved profile reads back the same`() {
        assertEquals(PatientProfile.SAMPLE, decodeProfile(encodeProfile(PatientProfile.SAMPLE)))
    }

    @Test
    fun `line breaks, backslashes, equals signs and bars in values survive saving`() {
        val tricky = PatientProfile(
            fullName = "A = B",
            notes = "Line one\nLine two \\ end",
            people = listOf(Person("Friend|old", "Kabir")),
        )
        val back = decodeProfile(encodeProfile(tricky))
        assertEquals("A = B", back.fullName)
        assertEquals("Line one\nLine two \\ end", back.notes)
        assertEquals(Person("Friend/old", "Kabir"), back.people.single())
    }

    @Test
    fun `unknown keys and broken lines are skipped`() {
        val p = decodeProfile("fullName=Meera\nshoeSize=7\nno equals sign here\n\nlike=Tea")
        assertEquals(PatientProfile(fullName = "Meera", likes = listOf("Tea")), p)
    }

    @Test
    fun `the sample counts every filled detail`() {
        // 8 single fields minus none empty (fullName, called, gender, age, languages, livesIn, work, notes)
        // + 3 likes + 1 dislike + 4 people + 2 conditions + 2 allergies.
        assertEquals(20, PatientProfile.SAMPLE.detailCount)
        assertTrue(PatientProfile().isEmpty)
    }

    @Test
    fun `the short name is what they are called, else the first name`() {
        assertEquals("Ramu", PatientProfile.SAMPLE.shortName)
        assertEquals("Ramesh", PatientProfile(fullName = "Ramesh Iyer").shortName)
    }

    @Test
    fun `an empty profile adds nothing to the prompt`() {
        assertNull(profilePrompt(null))
        assertNull(profilePrompt(PatientProfile()))
    }

    @Test
    fun `the prompt names the person, their people, likes and health`() {
        val text = profilePrompt(PatientProfile.SAMPLE)!!
        assertTrue(text.contains("- Full name: Ramesh Iyer."))
        assertTrue(text.contains("- Family and friends call them Ramu."))
        assertTrue(text.contains("- Asked their name, one reply gives the full name: \"My name is Ramesh Iyer\"."))
        assertTrue(text.contains("- 62 years old, male, lives in Bengaluru, used to work as school teacher, speaks English, Hindi."))
        assertTrue(text.contains("People close to them: wife Meera, son Arjun, friend Kabir, day nurse Sister Anita."))
        assertTrue(text.contains("Likes: Chai, no sugar; Cricket; Old film songs."))
        assertTrue(text.contains("Health: ALS; Type 2 diabetes."))
        assertTrue(text.contains("Allergic to: Penicillin; Peanuts. Never suggest these."))
        assertTrue(text.contains("never invent others"))
    }

    @Test
    fun `only filled parts reach the prompt`() {
        val text = profilePrompt(PatientProfile(people = listOf(Person("Daughter", "Asha"))))!!
        assertTrue(text.contains("daughter Asha"))
        assertFalse(text.contains("Likes"))
        assertFalse(text.contains("Health"))
    }

    @Test
    fun `the editor lists take one item per line and keep commas inside an item`() {
        assertEquals(listOf("Chai, no sugar", "Cricket"), splitList("Chai, no sugar\n\n  Cricket \n"))
        assertEquals(listOf(Person("Wife", "Meera"), Person("", "Kabir")), parsePeople("Wife: Meera\nKabir\n"))
        assertEquals("Wife: Meera\nKabir", peopleText(listOf(Person("Wife", "Meera"), Person("", "Kabir"))))
    }
}
