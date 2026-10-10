package com.outspoken.suggest

import com.outspoken.conversation.AppLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cards in Hindi: the model is asked to write Hindi, and Hindi text survives every check. */
class HindiTest {

    private val heardInHindi = listOf(Turn(true, "क्या आपको दर्द हो रहा है?"))

    private class FakeModel(private val answer: String) : TextModel {
        override suspend fun generate(prompt: String) = Generation(answer, tokensPerSecond = 50f)
    }

    @Test
    fun `the reply prompt asks for Hindi with a Hindi example, and English does not`() {
        val hindi = buildPrompt(SuggestionRequest(heardInHindi, hourOfDay = 10, language = AppLanguage.Hindi))
        assertTrue(hindi.contains(HINDI_LINE))
        assertTrue(hindi.contains(EXAMPLE_ANSWER_HINDI))
        val english = buildPrompt(SuggestionRequest(listOf(Turn(true, "Are you in pain?")), hourOfDay = 10))
        assertFalse(english.contains(HINDI_LINE))
        assertTrue(english.contains(EXAMPLE_ANSWER))
    }

    @Test
    fun `a Hindi question starting with kya asks yes or no`() {
        assertTrue(isYesNoQuestion("क्या आपको दर्द हो रहा है?"))
        assertFalse(isYesNoQuestion("आप कैसा महसूस कर रहे हैं?"))
    }

    @Test
    fun `Hindi replies are kept, not dropped as matching the line just said`() = runBlocking {
        // With a-z only, every Hindi reply read as "" and matched the Hindi line just said.
        val said = heardInHindi + Turn(false, "मुझे पानी चाहिए")
        val answer = """["हाँ, थोड़ा दर्द है", "नहीं, मैं ठीक हूँ", "पीठ में दर्द है", "कृपया दवा दीजिए"]"""
        val result = ModelSuggestionEngine(FakeModel(answer)) { 0L }.suggest(SuggestionRequest(said, 10, AppLanguage.Hindi))
        assertTrue(result.fromModel)
        assertEquals(4, result.modelReplies)
        assertEquals("हाँ, थोड़ा दर्द है", result.replies.first())
    }

    @Test
    fun `the Hindi line just said is still not offered again`() = runBlocking {
        val said = heardInHindi + Turn(false, "मुझे पानी चाहिए")
        val answer = """["मुझे पानी चाहिए", "नहीं, मैं ठीक हूँ"]"""
        val result = ModelSuggestionEngine(FakeModel(answer)) { 0L }.suggest(SuggestionRequest(said, 10, AppLanguage.Hindi))
        assertFalse(result.replies.contains("मुझे पानी चाहिए"))
        assertEquals(1, result.modelReplies)
    }

    @Test
    fun `a failed Hindi answer falls back to the Hindi phrase bank`() = runBlocking {
        val result = ModelSuggestionEngine(FakeModel("no")) { 0L }.suggest(SuggestionRequest(heardInHindi, 10, AppLanguage.Hindi))
        assertFalse(result.fromModel)
        assertEquals(AppLanguage.Hindi.phrases.take(4), result.replies)
    }

    @Test
    fun `Say anything reads on from Hindi words`() {
        val prompt = buildWordPrompt(WordRequest(emptyList(), "मुझे", AppLanguage.Hindi))
        assertTrue(prompt.contains(HINDI_LINE))
        assertTrue(prompt.contains(WORD_EXAMPLE_ANSWER_HINDI))
        val answer = parseWordAnswer("""["मुझे पानी चाहिए", "मुझे दर्द हो रहा है", "मैं ठीक हूँ"]""", "मुझे")
        assertEquals("मुझे पानी चाहिए", answer.completion)
        assertEquals(listOf("पानी चाहिए", "दर्द हो"), answer.words)
        assertEquals(listOf("मैं ठीक हूँ"), answer.unfit)
        assertEquals(listOf("पानी"), parseWordAnswer("""["मुझे पानी। अभी"]""", "मुझे").words)
    }
}
