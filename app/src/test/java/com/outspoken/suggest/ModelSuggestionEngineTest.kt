package com.outspoken.suggest

import com.outspoken.conversation.PhraseBank
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSuggestionEngineTest {

    private val good = """["My back hurts", "My head hurts", "It is getting worse", "I need medicine"]"""
    private val request = SuggestionRequest(listOf(Turn(false, "I am in pain")), hourOfDay = 10)
    private var clock = 0L

    private class FakeModel(private val answers: List<() -> String>, private val onCall: () -> Unit = {}) : TextModel {
        var calls = 0
        override suspend fun generate(prompt: String): Generation {
            onCall()
            return Generation(answers[calls++](), tokensPerSecond = 20f)
        }
    }

    @Test
    fun `good answer is used`() = runBlocking {
        val model = FakeModel(listOf({ good }), onCall = { clock += 1_400 })
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertTrue(result.fromModel)
        assertEquals("My back hurts", result.replies.first())
        assertEquals(1_400, result.elapsedMs)
        assertEquals(20f, result.tokensPerSecond)
        assertEquals(1, model.calls)
    }

    @Test
    fun `a near miss is used and topped up without asking again`() = runBlocking {
        val model = FakeModel(listOf({ "1. My back hurts\n2. My head hurts" }, { good }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertTrue(result.fromModel)
        assertEquals(listOf("My back hurts", "My head hurts", PhraseBank.phrases[0], PhraseBank.phrases[1]), result.replies)
        assertEquals(1, model.calls)
    }

    @Test
    fun `an unusable answer falls back to the phrase bank without asking again`() = runBlocking {
        val model = FakeModel(listOf({ "no" }, { good }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertFalse(result.fromModel)
        assertEquals(PhraseBank.phrases.take(4), result.replies)
        assertEquals(1, model.calls)
    }

    @Test
    fun `top up skips phrases the model already gave`() = runBlocking {
        val model = FakeModel(listOf({ """["I need water", "Thank you"]""" }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertEquals(4, result.replies.size)
        assertEquals(4, result.replies.map { it.lowercase() }.toSet().size)
        assertEquals(listOf("I need water", "Thank you"), result.replies.take(2))
    }

    @Test
    fun `model error falls back at once`() = runBlocking {
        val model = FakeModel(listOf({ throw IllegalStateException("boom") }, { good }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertFalse(result.fromModel)
        assertEquals(1, model.calls)
    }

    @Test
    fun `frequent phrases are prioritized in top up and fallback`() = runBlocking {
        val model = FakeModel(listOf({ """["My back hurts"]""" }, { "bad" }))
        val engine = ModelSuggestionEngine(model, frequentPhrases = { listOf("Thank you") }) { clock }
        val toppedUp = engine.suggest(request)
        assertEquals(listOf("My back hurts", "Thank you", PhraseBank.phrases[0], PhraseBank.phrases[1]), toppedUp.replies)

        val fallback = engine.suggest(request)
        assertEquals("Thank you", fallback.replies.first())
    }
}
