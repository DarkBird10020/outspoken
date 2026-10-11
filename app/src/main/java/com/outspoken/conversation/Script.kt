package com.outspoken.conversation

/**
 * The writing systems of the languages the app offers, told apart by their letters. A sentence
 * the recogniser wrote in another language's letters (English in Hindi letters, phone 19:18) is
 * found by comparing its script with its language's.
 */
enum class Script(val promptName: String) {
    Latin("Latin"),
    Devanagari("Devanagari"),
    Bengali("Bengali"),
    Gujarati("Gujarati"),
    Tamil("Tamil"),
    Telugu("Telugu"),
    Kannada("Kannada"),
    Malayalam("Malayalam"),
    Arabic("Arabic"),
    Han("Chinese characters"),
    Japanese("Japanese (kana and kanji)"),
    ;

    companion object {
        /** The script of one letter; null for digits, marks, spaces and letters of other scripts. */
        fun of(c: Char): Script? = when (c) {
            in 'a'..'z', in 'A'..'Z', in 'À'..'ɏ' -> Latin
            in 'ऀ'..'ॿ' -> Devanagari
            in 'ঀ'..'৿' -> Bengali
            in '઀'..'૿' -> Gujarati
            in '஀'..'௿' -> Tamil
            in 'ఀ'..'౿' -> Telugu
            in 'ಀ'..'೿' -> Kannada
            in 'ഀ'..'ൿ' -> Malayalam
            in '؀'..'ۿ', in 'ݐ'..'ݿ' -> Arabic
            in '぀'..'ヿ' -> Japanese
            in '一'..'鿿', in '㐀'..'䶿' -> Han
            else -> null
        }
    }
}

/**
 * The script most letters of [text] are in; null when it has no letters of a known script. Any
 * kana makes it Japanese, since Japanese mixes kana with the Chinese characters it shares.
 */
fun dominantScript(text: String): Script? {
    val counts = mutableMapOf<Script, Int>()
    for (c in text) Script.of(c)?.let { counts[it] = (counts[it] ?: 0) + 1 }
    if (Script.Japanese in counts) return Script.Japanese
    return counts.maxByOrNull { it.value }?.key
}
