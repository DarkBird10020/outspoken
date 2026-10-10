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
    /** The model's sentences the words came from, and those that did not fit, for the log. */
    val sentences: List<String> = emptyList(),
    val unfit: List<String> = emptyList(),
)

/** A next-word card holds at most this many words of a sentence the model wrote. */
const val MAX_NEXT_WORDS = 2

/** The finished sentence is kept to one spoken line. */
const val MAX_SENTENCE_WORDS = 14

/** The model adds at most this many words to the sentence so far, which keeps its answer short. */
const val MAX_ADDED_WORDS = 6

/**
 * Asks for whole sentences that start with the words so far, not for loose next words. Asked for
 * next words, the model gave ones that did not fit after the sentence (phone logs, 08:39 and
 * 10:17 to 10:18): "and" and "Or perhaps tea" as first words, "a little tired" after "I" (the
 * person picked it and got "I a little tired"), "a little water" after "A little", and "Water or"
 * after "Please food". Its whole sentences were good ("I am feeling a little tired now."), so the
 * next-word cards are now cut from them by [parseWordAnswer].
 */
fun buildWordPrompt(request: WordRequest): String = buildString {
    appendLine("You help a person in bed who cannot move or speak say a sentence of their own, a few words at a time. They pick by blinking, and the phone says it aloud. A family member or a nurse (the Visitor) is with them.")
    appendLine("Write 4 different sentences the Person may want to say, the most likely first.")
    appendLine("Each sentence: starts with exactly the words so far, then adds at most $MAX_ADDED_WORDS more words. First person, plain everyday words, said by the Person, never by the Visitor.")
    appendLine("Make the 4 sentences go on differently right after the words so far.")
    appendLine("Answer with only a JSON list of 4 strings.")
    appendLine()
    // One worked example: small models copy the shape of an example far more reliably than they
    // follow a description of it.
    appendLine("Example. Words so far: \"I want\"")
    appendLine("Answer: $WORD_EXAMPLE_ANSWER")
    appendLine()
    val turns = request.turns.takeLast(PROMPT_TURNS)
    if (turns.isNotEmpty()) {
        appendLine("Conversation so far:")
        turns.forEach { appendLine("${if (it.fromListener) "Visitor" else "Person"}: ${it.text}") }
    }
    if (request.sentence.isBlank()) {
        appendLine("No words yet: each sentence may start any way, and the 4 should start differently.")
    } else {
        appendLine("Words so far: \"${request.sentence}\"")
    }
    append("Answer:")
}

const val WORD_EXAMPLE_ANSWER = """["I want some water, please", "I want to sleep now", "I want my family here", "I am cold"]"""

/**
 * What a word answer gave: the finished sentence, up to four next words, and the model's sentences
 * that fitted and did not (for the log: at 10:30:47 only 1 of 4 fitted "I need to go", and the log
 * could not say why).
 */
data class WordAnswer(
    val completion: String?,
    val words: List<String>,
    val sentences: List<String> = emptyList(),
    val unfit: List<String> = emptyList(),
)

/**
 * Reads the model's sentences for "Say anything". Only a sentence whose first words are exactly
 * the sentence so far, word for word, is used; the first of those is the finished sentence
 * ("Finish it for me"). Each one gives a next-word card: its next one or two words, cut at a
 * comma or full stop, so a card always reads on from the sentence so far. Repeats, and a card that
 * only repeats the word just picked, are dropped. Anything else is left out, and the built-in
 * words fill the gap.
 */
fun parseWordAnswer(output: String, sentence: String): WordAnswer {
    val start = output.indexOf('[')
    val end = output.lastIndexOf(']')
    val items = (if (start >= 0 && end > start) parseStringList(output.substring(start, end + 1)) else null)
        ?: Regex("\"([^\"\n]{1,120})\"").findAll(output).map { it.groupValues[1] }.toList()
    val soFar = tokens(sentence).map(::normal)
    val (fits, unfit) = items
        .map { it.trim().trim('"').trim() }
        .partition { item ->
            val words = tokens(item)
            words.size in (soFar.size + 1)..MAX_SENTENCE_WORDS && words.take(soFar.size).map(::normal) == soFar
        }
    val fitting = fits.distinctBy { tokens(it).joinToString(" ", transform = ::normal) }
    val last = soFar.lastOrNull()
    val words = fitting
        .map { nextWords(tokens(it).drop(soFar.size)) }
        .filter { it.isNotEmpty() && normal(it) != last }
        .distinctBy { normal(it) }
        .take(4)
    return WordAnswer(fitting.firstOrNull(), words, fitting, unfit)
}

/** The first one or two of [rest], stopping after a word that ends a phrase, without its mark. */
private fun nextWords(rest: List<String>): String {
    val picked = mutableListOf<String>()
    for (word in rest) {
        val bare = word.trimEnd(*PHRASE_END)
        if (bare.isNotEmpty()) picked += bare
        if (picked.size == MAX_NEXT_WORDS || bare.length < word.length) break
    }
    return picked.joinToString(" ")
}

private val PHRASE_END = charArrayOf(',', '.', '!', '?', ';', ':')

private fun tokens(text: String) = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

private fun normal(text: String) = text.lowercase().replace(Regex("[^a-z0-9' ]"), " ").trim().replace(Regex(" +"), " ")
