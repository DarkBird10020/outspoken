package com.outspoken.conversation

/**
 * "Say anything": a sentence built a word at a time. Four next words show at once from the
 * built-in list ([CommonWords]); the model's words and a finished sentence ("Finish it for me")
 * replace them when they arrive. Card ids: 0 to 3 are the words, then the fixed cards below.
 */
class SentenceBuilder(private val builtIn: (List<String>) -> List<String> = CommonWords::next) {

    private val words = mutableListOf<String>()
    private var modelWords: List<String> = emptyList()
    private var page = 0

    /** The model's guess at the whole sentence; null until it answers. */
    var completion: String? = null
        private set

    val sentence: String get() = words.joinToString(" ").replaceFirstChar { it.uppercase() }

    val wordCount: Int get() = words.sumOf { it.split(' ').count(String::isNotBlank) }

    val isEmpty: Boolean get() = words.isEmpty()

    /** Model words first, then the built-in ones, without repeats or the word just added. */
    private val pool: List<String>
        get() {
            val last = words.lastOrNull()?.lowercase()
            return (modelWords + builtIn(words))
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.lowercase() != last }
                .distinctBy { it.lowercase() }
        }

    /** The four words shown now. */
    val shown: List<String>
        get() {
            val pages = pool.chunked(WORD_COUNT)
            return pages.getOrElse(page) { pages.firstOrNull().orEmpty() }
        }

    /** Cards in scan order: the words, More words, Delete, Speak (once there is a word), Finish (once offered). */
    val cards: List<Int>
        get() = shown.indices.toList() +
            listOf(MORE_WORDS, DELETE) +
            listOfNotNull(SPEAK.takeIf { words.isNotEmpty() }, FINISH.takeIf { completion != null })

    fun add(word: String) {
        words += word.trim()
        clearSuggestions()
    }

    /** Removes the last word; false when there was nothing to remove (the person wants out). */
    fun deleteLast(): Boolean {
        if (words.isEmpty()) return false
        words.removeAt(words.lastIndex)
        clearSuggestions()
        return true
    }

    /** The next four words, wrapping round to the first four. */
    fun more() {
        val pages = pool.chunked(WORD_COUNT).size.coerceAtLeast(1)
        page = (page + 1) % pages
    }

    /** The model's answer for the sentence as it is now. */
    fun showModel(next: List<String>, finished: String?) {
        modelWords = next
        completion = finished?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != sentence.lowercase() }
            ?.replaceFirstChar { it.uppercase() }
        page = 0
    }

    private fun clearSuggestions() {
        modelWords = emptyList()
        completion = null
        page = 0
    }

    companion object {
        const val WORD_COUNT = 4
        const val MORE_WORDS = 10
        const val DELETE = 11
        const val SPEAK = 12
        const val FINISH = 13
    }
}

/**
 * The built-in next words, shown while the model thinks and kept when it fails, the way the
 * phrase bank works for replies. Chosen for a person in bed being cared for.
 */
object CommonWords {

    private val starters = listOf(
        "I", "Please", "My", "Can you", "I need", "I want", "I feel", "Thank you",
        "Yes", "No", "Where is", "When",
    )

    private val after = mapOf(
        "i" to listOf("want", "need", "am", "feel", "have", "can't", "like", "love"),
        "want" to listOf("to", "some", "water", "my", "to sleep", "to go", "to sit up", "more"),
        "need" to listOf("help", "water", "the toilet", "to", "my", "a doctor", "the nurse", "rest"),
        "am" to listOf("tired", "in pain", "hungry", "thirsty", "cold", "hot", "okay", "scared"),
        "feel" to listOf("sick", "tired", "better", "pain", "cold", "hot", "okay", "dizzy"),
        "to" to listOf("sleep", "eat", "drink", "go", "see", "talk", "rest", "sit up"),
        "please" to listOf("help", "call", "bring", "stop", "wait", "turn", "open", "close"),
        "my" to listOf("back", "head", "leg", "arm", "chest", "family", "phone", "glasses"),
        "can" to listOf("you", "I", "we", "someone"),
        "you" to listOf("please", "help", "bring", "call", "turn", "open", "stay", "sit"),
        "the" to listOf("nurse", "doctor", "light", "window", "door", "TV", "fan", "bed"),
        "call" to listOf("the nurse", "my family", "the doctor", "my son", "my daughter"),
        "is" to listOf("my", "the", "it", "there"),
        "thank" to listOf("you", "you so much"),
    )

    private val anywhere = listOf("please", "now", "a little", "more", "not", "very", "help", "water", "pain", "home", "and", "the")

    /** Words that may come next after [words] (each item may hold more than one word). */
    fun next(words: List<String>): List<String> {
        val last = words.lastOrNull()?.trim()?.split(' ')?.lastOrNull()?.lowercase() ?: return starters
        return after[last].orEmpty() + anywhere
    }
}
