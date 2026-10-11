package com.outspoken.conversation

import com.outspoken.hand.HandAction
import com.outspoken.hand.HandSign
import com.outspoken.listen.LanguageVotes
import com.outspoken.profile.PatientProfile
import com.outspoken.suggest.SuggestionRequest
import com.outspoken.suggest.Turn
import com.outspoken.suggest.WordRequest
import com.outspoken.suggest.buildPrompt
import com.outspoken.suggest.buildWordPrompt
import com.outspoken.suggest.inWrongScript
import com.outspoken.suggest.parseRewrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every language the person can pick, and following the visitor between them. */
class LanguagesTest {

    @Test
    fun `every language has a full phrase bank, topics, fixed cards and words`() {
        AppLanguage.entries.forEach { language ->
            assertEquals("${language.name} phrases", 7, language.phrases.size)
            assertEquals("${language.name} yes no", 2, language.yesNo.size)
            assertEquals("${language.name} topics", 5, language.topics.size)
            assertEquals("${language.name} fixed cards", 3, language.fixedCards.size)
            assertTrue("${language.name} first words", language.firstWords.size >= 4)
            assertTrue("${language.name} words", language.anywhere.size >= 4)
            val all = language.phrases + language.yesNo + language.topics.flatMap { listOf(it.first, it.second) } + language.fixedCards
            assertTrue("${language.name} has blanks", all.none { it.isBlank() })
        }
    }

    @Test
    fun `every language's own words are written in its script`() {
        AppLanguage.entries.forEach { language ->
            (language.phrases + language.label).forEach { text ->
                assertFalse("${language.name}: \"$text\"", inWrongScript(text, language))
            }
        }
    }

    @Test
    fun `every hand sign that says something has a phrase in every language`() {
        val said = HandSign.entries.mapNotNull { (it.action as? HandAction.Say)?.text } + listOf("Thank you", "Please call the nurse")
        AppLanguage.entries.filter { it != AppLanguage.English }.forEach { language ->
            said.forEach { phrase -> assertTrue("${language.name} lacks \"$phrase\"", phrase in language.signPhrases) }
        }
    }

    @Test
    fun `codes are unique and the recogniser's tags find their language`() {
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map { it.code }.toSet().size)
        assertEquals(AppLanguage.Tamil, AppLanguage.fromCode("ta-IN"))
        assertEquals(AppLanguage.Chinese, AppLanguage.fromCode("cmn-Hans-CN"))
        assertEquals(AppLanguage.Spanish, AppLanguage.fromCode("es_US"))
        assertNull(AppLanguage.fromCode("xx-YY"))
        assertEquals("zh", LanguageVotes.primary("cmn-hans-cn"))
    }

    @Test
    fun `the language of a sentence is told by its letters among the chosen ones`() {
        val chosen = listOf(AppLanguage.English, AppLanguage.Tamil, AppLanguage.Hindi, AppLanguage.Marathi)
        assertEquals(AppLanguage.Tamil, languageOfText("உங்களுக்கு வலிக்கிறதா?", chosen))
        assertEquals(AppLanguage.English, languageOfText("Are you in pain?", chosen))
        // Hindi and Marathi share Devanagari: the one chosen first wins.
        assertEquals(AppLanguage.Hindi, languageOfText("क्या आपको दर्द है?", chosen))
        assertEquals(AppLanguage.Hindi, languageOfText("kya aapko dard hai", chosen))
        // Not chosen: no language is guessed from it.
        assertNull(languageOfText("¿Le duele algo?", listOf(AppLanguage.Hindi, AppLanguage.Tamil)))
        assertEquals(AppLanguage.Japanese, languageOfText("痛いところはありますか？", listOf(AppLanguage.English, AppLanguage.Japanese)))
    }

    @Test
    fun `a sentence in another language's letters is caught, and a good rewrite is taken`() {
        assertTrue(inWrongScript("व्हाट्स योर नेम", AppLanguage.English))
        assertTrue(inWrongScript("ninga eppadi irukkinga", AppLanguage.Tamil))
        assertFalse(inWrongScript("நீங்கள் எப்படி இருக்கிறீர்கள்?", AppLanguage.Tamil))
        assertEquals("நீங்கள் எப்படி இருக்கிறீர்கள்?", parseRewrite("நீங்கள் எப்படி இருக்கிறீர்கள்?", "ninga eppadi irukkinga", AppLanguage.Tamil))
        assertNull(parseRewrite("Ninga eppadi irukkinga?", "ninga eppadi irukkinga", AppLanguage.Tamil))
    }

    @Test
    fun `the model is told which language and script to write`() {
        val turns = listOf(Turn(true, "¿Le duele algo?"))
        val prompt = buildPrompt(SuggestionRequest(turns, 10, AppLanguage.Spanish, PatientProfile(gender = "Female")))
        assertTrue(prompt.contains("Write in Spanish, in Latin script"))
        assertTrue(prompt.contains("The Person is a woman"))
        assertTrue(prompt.contains("write your answer in Spanish"))
        val words = buildWordPrompt(WordRequest(emptyList(), "எனக்கு", AppLanguage.Tamil))
        assertTrue(words.contains("Write in Tamil, in Tamil script"))
        assertFalse(buildPrompt(SuggestionRequest(turns, 10)).contains("Write in"))
    }

    @Test
    fun `the board and Say anything use the chosen language's own words`() {
        val board = Board()
        board.useLanguage(AppLanguage.Telugu)
        assertEquals(AppLanguage.Telugu.phrases.take(4), board.replies)
        assertEquals(AppLanguage.Telugu.firstWords, CommonWords.forLanguage(AppLanguage.Telugu)(emptyList()))
        assertEquals(AppLanguage.Telugu.anywhere, CommonWords.forLanguage(AppLanguage.Telugu)(listOf("నాకు")))
    }

    @Test
    fun `saved language lists read back in order, unknown names skipped`() {
        assertEquals(listOf(AppLanguage.Tamil, AppLanguage.English), AppLanguage.listFromNames("Tamil, English,Klingon,Tamil"))
        assertEquals(emptyList<AppLanguage>(), AppLanguage.listFromNames(null))
    }
}
