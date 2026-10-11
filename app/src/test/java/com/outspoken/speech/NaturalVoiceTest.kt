package com.outspoken.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NaturalVoiceTest {

    private val heart = NaturalVoices.default
    private val alpha = NaturalVoices.all.first { it.language == "hi" }

    private fun voiceFor(text: String, cardLanguage: String = "en", on: Boolean = true, ready: Boolean = true, languages: Set<String> = NaturalVoices.languages) =
        NaturalVoices.voiceFor(text, cardLanguage, heart, on, ready, languages)

    @Test
    fun `english cards use the chosen natural voice`() {
        assertEquals(heart, voiceFor("I need some water now."))
    }

    @Test
    fun `the phone voice is used when the natural voice is off or not loaded`() {
        assertNull(voiceFor("I need some water now.", on = false))
        assertNull(voiceFor("I need some water now.", ready = false))
    }

    @Test
    fun `hindi cards use the phone voice while hindi is not one of the natural voice's languages`() {
        assertNull(voiceFor("मुझे पानी चाहिए", cardLanguage = "hi"))
        assertNull(voiceFor("Mujhe paani chahiye", cardLanguage = "hi"))
    }

    @Test
    fun `devanagari text never reaches an english voice, whatever the cards are set to`() {
        assertNull(voiceFor("मुझे पानी चाहिए", cardLanguage = "en"))
    }

    @Test
    fun `once hindi is on the list hindi cards get a hindi voice`() {
        assertEquals(alpha, voiceFor("मुझे पानी चाहिए", cardLanguage = "hi", languages = setOf("en", "hi")))
    }

    @Test
    fun `a language with no natural voice uses the phone voice`() {
        assertNull(voiceFor("Necesito agua", cardLanguage = "es", languages = setOf("en", "hi")))
    }

    @Test
    fun `every offered voice has a speaker number in the model and a language`() {
        NaturalVoices.all.forEach { assert(it.id in 0..52 && it.language in setOf("en", "hi")) { it.toString() } }
        assertEquals(NaturalVoices.all.size, NaturalVoices.all.map { it.id }.toSet().size)
    }
}

class AudioCacheTest {

    private fun key(text: String, voice: Int = 3, speed: Float = 1f) = AudioCache.Key(text, voice, speed)

    @Test
    fun `audio is kept per text, voice and speed`() {
        val cache = AudioCache<String>()
        cache.put(key("Yes"), "yes-3")
        assertEquals("yes-3", cache.get(key("Yes")))
        assertNull(cache.get(key("Yes", voice = 16)))
        assertNull(cache.get(key("Yes", speed = 1.1f)))
    }

    @Test
    fun `missing lists the cards not made yet, first card first, once each`() {
        val cache = AudioCache<String>()
        cache.put(key("No"), "no")
        assertEquals(listOf(key("Yes"), key("Thank you")), cache.missing(listOf(key("Yes"), key("No"), key("Thank you"), key("Yes"))))
    }

    @Test
    fun `the oldest audio goes first when the cache is full`() {
        val cache = AudioCache<String>(maxItems = 2)
        cache.put(key("a"), "a")
        cache.put(key("b"), "b")
        cache.get(key("a"))
        cache.put(key("c"), "c")
        assertEquals(2, cache.size)
        assertNull(cache.get(key("b")))
        assertEquals("a", cache.get(key("a")))
    }
}
