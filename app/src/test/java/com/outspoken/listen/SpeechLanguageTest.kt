package com.outspoken.listen

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguageTest {

    private val wanted = listOf("en-IN", "en-US")

    @Test
    fun `the first wanted language with its pack on the phone is used`() {
        assertEquals(LanguagePick("en-US", download = false), languageTag(wanted, installed = listOf("en-US", "hi-IN"), supported = listOf("en-IN", "en-US")))
        assertEquals(LanguagePick("en-IN", download = false), languageTag(wanted, installed = listOf("en_in", "en-US"), supported = emptyList()))
    }

    @Test
    fun `with no pack on the phone the first it can download is asked for`() {
        assertEquals(LanguagePick("hi-IN", download = true), languageTag(listOf("hi-IN"), installed = listOf("en-US"), supported = listOf("en-US", "hi-IN")))
    }

    @Test
    fun `when the phone has and offers nothing the first wanted language is tried as it is`() {
        assertEquals(LanguagePick("en-IN", download = false), languageTag(wanted, installed = emptyList(), supported = emptyList()))
    }

    @Test
    fun `error codes read as words in the log`() {
        assertEquals("no match", errorName(7))
        assertEquals("language pack missing", errorName(13))
        assertEquals("error 99", errorName(99))
    }
}
