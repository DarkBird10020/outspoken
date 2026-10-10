package com.outspoken.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechLanguageTest {

    private val english = listOf("en-IN", "en-US")
    private val hindi = listOf("hi-IN")

    // What the phone reported at 16:14:02: only US English on it, 30 packs to download.
    private val phoneHas = listOf("en-US")
    private val phoneOffers = listOf("en-US", "en-IN", "hi-IN")

    @Test
    fun `English listens in the pack on the phone and asks for Indian English`() {
        val plan = planSpeech(SpeechWish(english), phoneHas, phoneOffers)
        assertEquals("en-US", plan.listenIn)
        assertEquals(listOf("en-IN"), plan.download)
        assertFalse(plan.mainMissing)
        assertTrue(plan.hasPack)
    }

    @Test
    fun `Hindi without its pack keeps listening in English while the pack is asked for`() {
        // 16:15:28 the app listened in hi-IN with no pack, and heard nothing for the rest of the run.
        val plan = planSpeech(SpeechWish(hindi), phoneHas, phoneOffers)
        assertEquals("en-US", plan.listenIn)
        assertEquals(listOf("hi-IN"), plan.download)
        assertTrue(plan.mainMissing)
        assertTrue(plan.hasPack)
    }

    @Test
    fun `auto switches between the languages whose packs are on the phone`() {
        val plan = planSpeech(SpeechWish(english, listOf(hindi)), installed = listOf("en-US", "hi-IN"), supported = phoneOffers)
        assertEquals("en-US", plan.listenIn)
        assertEquals(listOf("hi-IN"), plan.switchTo)
        assertEquals(listOf("en-IN"), plan.download)
    }

    @Test
    fun `auto never switches to a language with no pack`() {
        val plan = planSpeech(SpeechWish(english, listOf(hindi)), phoneHas, phoneOffers)
        assertEquals(emptyList<String>(), plan.switchTo)
        assertEquals(listOf("en-IN", "hi-IN"), plan.download)
    }

    @Test
    fun `tags match whatever way the phone writes them`() {
        val plan = planSpeech(SpeechWish(english), installed = listOf("en_in"), supported = emptyList())
        assertEquals("en-IN", plan.listenIn)
        assertEquals(emptyList<String>(), plan.download)
    }

    @Test
    fun `a pack that failed is skipped and not asked for again`() {
        val plan = planSpeech(SpeechWish(english), installed = listOf("en-US", "en-IN"), supported = phoneOffers, broken = setOf("en-IN"))
        assertEquals("en-US", plan.listenIn)
        assertEquals(emptyList<String>(), plan.download)
    }

    @Test
    fun `with no pack at all it says so, for the normal recogniser to take over`() {
        val plan = planSpeech(SpeechWish(english), installed = emptyList(), supported = emptyList())
        assertEquals("en-IN", plan.listenIn)
        assertFalse(plan.hasPack)
    }

    @Test
    fun `codes read as words in the log`() {
        assertEquals("no match", errorName(7))
        assertEquals("language pack missing", errorName(13))
        assertEquals("error 99", errorName(99))
        assertEquals("sure", confidenceName(3))
        assertEquals("switched", switchName(1))
        assertEquals(null, switchName(0))
    }
}
