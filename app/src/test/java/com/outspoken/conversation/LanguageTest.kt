package com.outspoken.conversation

import com.outspoken.scan.Scanner
import org.junit.Assert.assertEquals
import org.junit.Test

/** Switching the cards to Hindi: phrase bank, yes / no and Say anything's first words. */
class LanguageTest {

    @Test
    fun `the board shows the Hindi phrase bank and yes no`() {
        val board = Board()
        board.useLanguage(AppLanguage.Hindi)
        assertEquals(AppLanguage.Hindi.phrases.take(4), board.replies)
        board.choose(Board.YES_NO)
        assertEquals(listOf("हाँ", "नहीं"), board.replies)
    }

    @Test
    fun `switching the language puts the cards in it and asks for new replies`() {
        var requests = 0
        val controller = ConversationController(speak = {}, scanner = Scanner(), requestReplies = { _, _ ->
            requests++
            true
        })
        controller.language = AppLanguage.Hindi
        controller.onTick(10_000)
        assertEquals(AppLanguage.Hindi.phrases.take(4), controller.ui.value.replies)
        assertEquals(1, requests)
        controller.language = AppLanguage.Hindi
        assertEquals(1, requests)
    }

    @Test
    fun `the visitor's language is told by the letters the recogniser wrote`() {
        assertEquals(AppLanguage.Hindi, spokenLanguage("क्या आपको दर्द हो रहा है?"))
        assertEquals(AppLanguage.English, spokenLanguage("Are you in pain?"))
        // Mostly Hindi with one English word, as people talk.
        assertEquals(AppLanguage.Hindi, spokenLanguage("क्या आपको pain है"))
        assertEquals(null, spokenLanguage("? 123"))
    }

    @Test
    fun `Say anything starts from Hindi first words`() {
        val builder = SentenceBuilder(CommonWords.forLanguage(AppLanguage.Hindi))
        assertEquals(AppLanguage.Hindi.firstWords.take(4), builder.shown)
        builder.add("मुझे")
        assertEquals("मुझे", builder.sentence)
    }
}
