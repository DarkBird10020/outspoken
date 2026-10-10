package com.outspoken.suggest

import com.outspoken.conversation.AppLanguage

/** One line of the conversation. */
data class Turn(val fromListener: Boolean, val text: String)

data class SuggestionRequest(val turns: List<Turn>, val hourOfDay: Int, val language: AppLanguage = AppLanguage.English)

/**
 * Four replies, and where they came from. [elapsedMs] runs from request to replies ready.
 * [timing] is the model's own breakdown of that time, for the log. [modelReplies] is how many of
 * [replies] the model wrote; the rest were topped up from the phrase bank.
 */
data class Suggestions(
    val replies: List<String>,
    val fromModel: Boolean,
    val elapsedMs: Long,
    val tokensPerSecond: Float? = null,
    val timing: String? = null,
    val modelReplies: Int = if (fromModel) replies.size else 0,
)

/** Conversation in, four replies out. The swap point for the on-device model. */
interface SuggestionEngine {
    suspend fun suggest(request: SuggestionRequest): Suggestions

    /** Next words for "Say anything". An engine without it gives none, and the built-in words show. */
    suspend fun nextWords(request: WordRequest): WordSuggestions = WordSuggestions(null, emptyList(), fromModel = false, elapsedMs = 0)
}

data class Generation(val text: String, val tokensPerSecond: Float? = null, val timing: String? = null)

/** A text-in, text-out language model runtime. */
interface TextModel {
    suspend fun generate(prompt: String): Generation
}
