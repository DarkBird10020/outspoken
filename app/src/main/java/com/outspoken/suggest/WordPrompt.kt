package com.outspoken.suggest

/** The sentence so far for "Say anything", with the conversation it belongs to. */
data class WordRequest(val turns: List<Turn>, val sentence: String)

/** The model's next words and its guess at the finished sentence, and how long that took. */
data class WordSuggestions(
    val completion: String?,
    val words: List<String>,
    val fromModel: Boolean,
    val elapsedMs: Long,
    val tokensPerSecond: Float? = null,
    val timing: String? = null,
)

/** Next words are short: one to three words each. */
const val MAX_NEXT_WORDS = 3

/** The finished sentence is kept to one spoken line. */
const val MAX_SENTENCE_WORDS = 14

fun buildWordPrompt(request: WordRequest): String = buildString {
    appendLine("You help a person in bed who cannot move or speak build a sentence a few words at a time. They pick by blinking, and the phone says the sentence aloud. A family member or a nurse (the Visitor) is with them.")
    appendLine("Answer with only a JSON list of 5 strings. First: the whole sentence finished the way the Person most likely means it, at most 12 words, starting with the sentence so far. Then: 4 different words or short phrases (1 to 3 words each) that could come right after the sentence so far.")
    appendLine()
    // One worked example: small models copy the shape of an example far more reliably than they
    // follow a description of it.
    appendLine("Example. Sentence so far: \"I want\"")
    appendLine("Answer: $WORD_EXAMPLE_ANSWER")
    appendLine()
    val turns = request.turns.takeLast(PROMPT_TURNS)
    if (turns.isNotEmpty()) {
        appendLine("Conversation so far:")
        turns.forEach { appendLine("${if (it.fromListener) "Visitor" else "Person"}: ${it.text}") }
    }
    if (request.sentence.isBlank()) {
        appendLine("The sentence is empty: the 4 words are good first words.")
    } else {
        appendLine("Sentence so far: \"${request.sentence}\"")
    }
    append("Answer:")
}

const val WORD_EXAMPLE_ANSWER = """["I want some water, please", "to", "water", "to sleep", "my family"]"""

/** What a word answer gave: the finished sentence when it fits, and up to four next words. */
data class WordAnswer(val completion: String?, val words: List<String>)

/**
 * Reads the model's answer for "Say anything". The finished sentence must start with the sentence
 * so far and be longer than it; next words must be one to three words and differ from each other
 * and from the word just picked. Anything else is dropped, and the built-in words fill the gap.
 */
fun parseWordAnswer(output: String, sentence: String): WordAnswer {
    val start = output.indexOf('[')
    val end = output.lastIndexOf(']')
    val items = (if (start >= 0 && end > start) parseStringList(output.substring(start, end + 1)) else null)
        ?: Regex("\"([^\"\n]{1,120})\"").findAll(output).map { it.groupValues[1] }.toList()
    val cleaned = items.map { it.trim().trim('"').trim() }.filter { it.isNotEmpty() }
    val soFar = normal(sentence)
    // With the full five, only the first may be the finished sentence; a short answer is searched.
    val completion = (if (cleaned.size > 4) cleaned.take(1) else cleaned).firstOrNull { item ->
        val n = normal(item)
        wordsIn(item) in 2..MAX_SENTENCE_WORDS && n.startsWith(soFar) && n.length > soFar.length
    }
    val last = soFar.substringAfterLast(' ')
    // Nor is the first of five ever a word, even when it did not fit as the finished sentence.
    val wordItems = if (cleaned.size > 4) cleaned.drop(1) else cleaned.filter { it != completion }
    val words = wordItems
        .filter { wordsIn(it) in 1..MAX_NEXT_WORDS }
        .filter { normal(it) != last }
        .distinctBy { normal(it) }
        .take(4)
    return WordAnswer(completion, words)
}

private fun normal(text: String) = text.lowercase().replace(Regex("[^a-z0-9' ]"), " ").trim().replace(Regex(" +"), " ")

private fun wordsIn(text: String) = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
