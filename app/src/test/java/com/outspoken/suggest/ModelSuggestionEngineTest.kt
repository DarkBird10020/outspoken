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
    fun `bad format is retried once`() = runBlocking {
        val model = FakeModel(listOf({ "My back hurts" }, { good }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertTrue(result.fromModel)
        assertEquals(2, model.calls)
    }

    @Test
    fun `two bad answers fall back to the phrase bank`() = runBlocking {
        val model = FakeModel(listOf({ "no" }, { "still no" }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertFalse(result.fromModel)
        assertEquals(PhraseBank.phrases.take(4), result.replies)
        assertEquals(2, model.calls)
    }

    @Test
    fun `model error falls back at once`() = runBlocking {
        val model = FakeModel(listOf({ throw IllegalStateException("boom") }, { good }))
        val result = ModelSuggestionEngine(model) { clock }.suggest(request)
        assertFalse(result.fromModel)
        assertEquals(1, model.calls)
    }
}
