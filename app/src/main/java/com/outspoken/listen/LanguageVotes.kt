package com.outspoken.listen

/**
 * The recogniser's language guesses while one sentence is heard. Each guess alone is often "not
 * sure", but together they are clear: listening in Hindi, an English question gave 32 guesses,
 * all en-in, and the recogniser still wrote it in Hindi letters ("व्हाट'स योर नेम", phone 19:18).
 */
class LanguageVotes(private val minVotes: Int = MIN_VOTES, private val share: Float = SHARE) {

    private val votes = mutableMapOf<String, Int>()

    fun add(tag: String) {
        val language = primary(tag)
        votes[language] = (votes[language] ?: 0) + 1
    }

    /** The language most guesses named, as a primary subtag ("en"), when they agree enough; else null. */
    fun dominant(): String? {
        val total = votes.values.sum()
        if (total < minVotes) return null
        val (language, count) = votes.maxByOrNull { it.value } ?: return null
        return language.takeIf { count >= total * share }
    }

    fun describe(): String = votes.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }

    fun clear() = votes.clear()

    companion object {
        const val MIN_VOTES = 5
        const val SHARE = 0.75f

        /** "en-IN" and "en_us" are both "en". */
        fun primary(tag: String): String = tag.replace('_', '-').substringBefore('-').lowercase()
    }
}

/** True when most letters in [text] are Devanagari. */
fun mostlyDevanagari(text: String): Boolean {
    val letters = text.filter { it.isLetter() }
    return letters.isNotEmpty() && letters.count(::isDevanagari) * 2 > letters.length
}

private fun isDevanagari(c: Char) = c in '\u0900'..'\u097F'
