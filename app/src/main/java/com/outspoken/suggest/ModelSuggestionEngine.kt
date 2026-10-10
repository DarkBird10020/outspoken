package com.outspoken.suggest

import com.outspoken.conversation.AppLanguage
import com.outspoken.conversation.spokenLanguage
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks the model once for four replies (PRD section 6, under 2 s). A near miss is used as far as
 * it goes and topped up from the phrase bank rather than asking again: a retry doubled the reply
 * time. Nothing usable, or a model error, gives the phrase bank.
 */
class ModelSuggestionEngine(
    private val model: TextModel,
    private val frequentPhrases: () -> List<String> = { emptyList() },
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
            return fallback(startMs, request.language)
        }
        // The line just said came straight back on the phone ("Fruit sounds good now.", 04:23:03).
        val lastSaid = request.turns.lastOrNull { !it.fromListener }?.text?.let(::words)
        val replies = extractReplies(generation.text).filterNot { words(it) == lastSaid }
        if (replies.isEmpty()) return fallback(startMs, request.language)
        return Suggestions(
            topUp(replies, lastSaid, request.language),
            fromModel = true,
            clockMs() - startMs,
            generation.tokensPerSecond,
            generation.timing,
            modelReplies = replies.size,
        )
    }

    override suspend fun nextWords(request: WordRequest): WordSuggestions {
        val startMs = clockMs()
        val generation = try {
            model.generate(buildWordPrompt(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return WordSuggestions(null, emptyList(), fromModel = false, clockMs() - startMs)
        }
        val answer = parseWordAnswer(generation.text, request.sentence)
        val usable = answer.completion != null || answer.words.isNotEmpty()
        return WordSuggestions(
            answer.completion,
            answer.words,
            usable,
            clockMs() - startMs,
            generation.tokensPerSecond,
            generation.timing,
            sentences = answer.sentences,
            unfit = answer.unfit,
        )
    }

    private fun topUp(replies: List<String>, lastSaid: String?, language: AppLanguage): List<String> {
        val pool = (frequentIn(language) + language.phrases).filterNot { words(it) == lastSaid }
        val candidates = pool.filter { phrase -> replies.none { it.equals(phrase, ignoreCase = true) } }.distinct()
        return (replies + candidates).take(REPLY_COUNT)
    }

    /**
     * Phrases said often, in the cards' language only: an English one said before topped up the
     * Hindi cards ("Yes, I can hear you.", 16:39:49).
     */
    private fun frequentIn(language: AppLanguage) = frequentPhrases().filter { spokenLanguage(it) == language }

    private fun words(text: String) = text.lowercase().replace(NOT_A_WORD, " ").trim().replace(SPACES, " ")

    private fun fallback(startMs: Long, language: AppLanguage): Suggestions {
        val pool = (frequentIn(language) + language.phrases).distinct()
        return Suggestions(pool.take(REPLY_COUNT), fromModel = false, clockMs() - startMs)
    }

    private companion object {
        // Letters of any script: with a-z only, every Hindi reply read as "" and matched the
        // Hindi line just said, so all of them were dropped.
        val NOT_A_WORD = Regex("[^\\p{L}\\p{M}\\p{N}' ]")
        val SPACES = Regex(" +")
    }
}
