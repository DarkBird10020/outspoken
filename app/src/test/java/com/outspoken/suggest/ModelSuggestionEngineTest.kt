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
        // PhraseBank.phrases[1] is "I am in pain", the line just said in [request], so it is skipped.
        assertEquals(listOf("My back hurts", "My head hurts", PhraseBank.phrases[0], PhraseBank.phrases[2]), result.replies)
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
    fun `the line just said is not offered again`() = runBlocking {
        val said = SuggestionRequest(listOf(Turn(true, "Breakfast?"), Turn(false, "Fruit sounds good now.")), hourOfDay = 4)
        val model = FakeModel(listOf({ """["fruit sounds good now", "Fruit is a good idea.", "Okay, I will eat fruit.", "When should I eat fruit?"]""" }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(said)
        assertEquals(listOf("Fruit is a good idea.", "Okay, I will eat fruit.", "When should I eat fruit?", PhraseBank.phrases[0]), result.replies)
        assertTrue(result.fromModel)
    }

    @Test
    fun `the top up skips the line just said too`() = runBlocking {
        val said = SuggestionRequest(listOf(Turn(false, PhraseBank.phrases[0])), hourOfDay = 10)
        val model = FakeModel(listOf({ """["Thank you"]""" }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(said)
        assertFalse(result.replies.contains(PhraseBank.phrases[0]))
        assertEquals(4, result.replies.size)
    }

    @Test
    fun `the model's timing is passed on for the log`() = runBlocking {
        val model = object : TextModel {
            override suspend fun generate(prompt: String) = Generation(good, 60f, "prompt 300 tok at 900 tok/s, first token 0.35 s, reply 40 tok")
        }
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertEquals("prompt 300 tok at 900 tok/s, first token 0.35 s, reply 40 tok", result.timing)
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
        assertEquals(listOf("My back hurts", "Thank you", PhraseBank.phrases[0], PhraseBank.phrases[2]), toppedUp.replies)

        val fallback = engine.suggest(request)
        assertEquals("Thank you", fallback.replies.first())
    }
}
