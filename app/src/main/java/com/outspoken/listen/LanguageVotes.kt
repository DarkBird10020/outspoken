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

/**
 * Devanagari in Latin letters, sound for sound. Used when the recogniser, listening in Hindi,
 * wrote English speech in Hindi letters: "व्हाट'स योर नेम" becomes "vhaat's yor nem", which the
 * model and the person can still read. Other characters pass through unchanged.
 */
fun romanise(text: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val consonant = CONSONANTS[c]
        if (consonant != null) {
            out.append(consonant)
            var next = text.getOrNull(i + 1)
            if (next == NUKTA) {
                i++
                next = text.getOrNull(i + 1)
            }
            when {
                next == VIRAMA -> i++
                next != null && next in MATRAS -> {
                    out.append(MATRAS.getValue(next))
                    i++
                }
                // A word-final consonant drops the inherent "a" in Hindi ("नेम" is "nem").
                next == null || !isDevanagari(next) -> Unit
                else -> out.append('a')
            }
        } else {
            out.append(VOWELS[c] ?: SIGNS[c] ?: c.toString())
        }
        i++
    }
    return out.toString()
}

/** True when most letters in [text] are Devanagari. */
fun mostlyDevanagari(text: String): Boolean {
    val letters = text.filter { it.isLetter() }
    return letters.isNotEmpty() && letters.count(::isDevanagari) * 2 > letters.length
}

private fun isDevanagari(c: Char) = c in 'ऀ'..'ॿ'

private const val VIRAMA = '्'
private const val NUKTA = '़'

private val VOWELS = mapOf(
    'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
    'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e",
)

private val MATRAS = mapOf(
    'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
    'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e",
)

private val SIGNS = mapOf('ं' to "n", 'ँ' to "n", 'ः' to "h", '।' to ".", '॥' to ".")

private val CONSONANTS = mapOf(
    'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
    'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
    'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
    'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
    'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
    'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
    'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
)
