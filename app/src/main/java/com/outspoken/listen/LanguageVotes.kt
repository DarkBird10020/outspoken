package com.outspoken.listen

import com.outspoken.conversation.AppLanguage

/**
 * The recogniser's language guesses while one sentence is heard. Each guess alone is often "not
 * sure", but together they are clear: listening in Hindi, an English question gave 32 guesses,
 * all en-in, and the recogniser still wrote it in Hindi letters ("व्हाट'स योर नेम", phone 19:18).
 */
class LanguageVotes(private val minVotes: Int = MIN_VOTES, private val share: Float = SHARE) {

    private val votes = mutableMapOf<String, Int>()

    /** The last guess the recogniser was at least fairly sure of, for a sentence too short to vote. */
    private var sure: String? = null

    fun add(tag: String) {
        val language = primary(tag)
        votes[language] = (votes[language] ?: 0) + 1
    }

    /**
     * A guess the recogniser was at least fairly sure of. A short Hindi question ("Tumhare kya
     * Halchal hai", 06:32:12) came after one such guess and too few others to vote, so it was not
     * taken as Hindi and stayed in English letters.
     */
    fun addSure(tag: String) {
        add(tag)
        sure = primary(tag)
    }

    /**
     * The language most guesses named, as a primary subtag ("en"), when they agree enough; else
     * the last sure guess; else null.
     */
    fun dominant(): String? {
        val total = votes.values.sum()
        val voted = if (total < minVotes) null else votes.maxByOrNull { it.value }?.takeIf { it.value >= total * share }?.key
        return voted ?: sure
    }

    fun describe(): String = votes.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }

    fun clear() {
        votes.clear()
        sure = null
    }

    companion object {
        const val MIN_VOTES = 5
        const val SHARE = 0.75f

        /** "en-IN" and "en_us" are both "en"; "cmn-Hans-CN" is "zh", the app's code for Chinese. */
        fun primary(tag: String): String =
            AppLanguage.fromCode(tag)?.code ?: tag.replace('_', '-').substringBefore('-').lowercase()
    }
}

/** True when most letters in [text] are Devanagari. */
fun mostlyDevanagari(text: String): Boolean {
    val letters = text.filter { it.isLetter() }
    return letters.isNotEmpty() && letters.count(::isDevanagari) * 2 > letters.length
}

private fun isDevanagari(c: Char) = c in '\u0900'..'\u097F'
