package com.outspoken.suggest

import com.outspoken.conversation.PhraseBank
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks the model for four replies. A badly formatted answer gets one retry; after that, or on
 * any model error, the phrase bank is used (PRD section 6).
 */
class ModelSuggestionEngine(
    private val model: TextModel,
    private val clockMs: () -> Long,
) : SuggestionEngine {

    override suspend fun suggest(request: SuggestionRequest): Suggestions {
        val startMs = clockMs()
        val prompt = buildPrompt(request)
        repeat(ATTEMPTS) {
            val generation = try {
                model.generate(prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return fallback(startMs)
            }
            parseReplies(generation.text)?.let { replies ->
                return Suggestions(replies, fromModel = true, clockMs() - startMs, generation.tokensPerSecond)
            }
        }
        return fallback(startMs)
    }

    private fun fallback(startMs: Long) =
        Suggestions(PhraseBank.phrases.take(REPLY_COUNT), fromModel = false, clockMs() - startMs)

    private companion object {
        const val ATTEMPTS = 2
    }
}
