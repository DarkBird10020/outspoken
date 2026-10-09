package com.outspoken.suggest

import com.outspoken.conversation.PhraseBank
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks the model once for four replies (PRD section 6, under 2 s). A near miss is used as far as
 * it goes and topped up from the phrase bank rather than asking again: a retry doubled the reply
 * time. Nothing usable, or a model error, gives the phrase bank.
 */
class ModelSuggestionEngine(
    private val model: TextModel,
    private val clockMs: () -> Long,
) : SuggestionEngine {

    override suspend fun suggest(request: SuggestionRequest): Suggestions {
        val startMs = clockMs()
        val prompt = buildPrompt(request)
        val generation = try {
            model.generate(prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return fallback(startMs)
        }
        val replies = extractReplies(generation.text)
        if (replies.isEmpty()) return fallback(startMs)
        return Suggestions(topUp(replies), fromModel = true, clockMs() - startMs, generation.tokensPerSecond)
    }

    private fun topUp(replies: List<String>): List<String> =
        (replies + PhraseBank.phrases.filter { phrase -> replies.none { it.equals(phrase, ignoreCase = true) } })
            .take(REPLY_COUNT)

    private fun fallback(startMs: Long) =
        Suggestions(PhraseBank.phrases.take(REPLY_COUNT), fromModel = false, clockMs() - startMs)
}
