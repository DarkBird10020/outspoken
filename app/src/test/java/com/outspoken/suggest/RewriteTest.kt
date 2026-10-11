package com.outspoken.suggest

import com.outspoken.conversation.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewriteTest {

    @Test
    fun `the prompt asks for the sentence in the language it was spoken in`() {
        val prompt = buildRewritePrompt("व्हाट'स योर नेम", AppLanguage.English)
        assertTrue(prompt.contains("speak English but wrote the sounds in Hindi (Devanagari) letters"))
        assertTrue(prompt.contains("Heard: व्हाट'स योर नेम"))
        assertTrue(prompt.contains("Do not translate it or answer it"))
        assertTrue(prompt.endsWith("Sentence:"))
        assertTrue(buildRewritePrompt("kya aap theek hain", AppLanguage.Hindi).contains("in Hindi, in Devanagari script"))
    }

    @Test
    fun `an english sentence in english letters is taken`() {
        assertEquals("What's your name?", parseRewrite(" What's your name?\n", "व्हाट'स योर नेम", AppLanguage.English))
        assertEquals("How are you feeling right now?", parseRewrite("Sentence: \"How are you feeling right now?\"", "हे हाउ अरे यू फीलिंग राइट नऊ", AppLanguage.English))
    }

    @Test
    fun `a hindi sentence must come back in devanagari`() {
        assertEquals("क्या आप ठीक हैं?", parseRewrite("क्या आप ठीक हैं?", "kya aap theek hain", AppLanguage.Hindi))
        assertNull(parseRewrite("Kya aap theek hain?", "kya aap theek hain", AppLanguage.Hindi))
    }

    @Test
    fun `mixed letters, an empty answer or a long answer are refused`() {
        assertNull(parseRewrite("व्हाट is your name", "व्हाट'स योर नेम", AppLanguage.English))
        assertNull(parseRewrite("   ", "व्हाट'स योर नेम", AppLanguage.English))
        assertNull(parseRewrite("My name is Ramesh and I live in Bengaluru with my wife and son.", "व्हाट'स योर नेम", AppLanguage.English))
    }

    @Test
    fun `mostly latin tells the scripts apart`() {
        assertTrue(mostlyLatin("kya aap theek hain"))
        assertFalse(mostlyLatin("क्या आप ठीक हैं"))
        assertFalse(mostlyLatin("42"))
    }
}
