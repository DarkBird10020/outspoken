package com.outspoken.speech

/** A voice in the Kokoro model: its speaker number there, a name to show, and its language (ISO 639). */
data class KokoroVoice(val id: Int, val label: String, val language: String)

/**
 * The natural voice (Kokoro v1.0 through sherpa-onnx). The model has 53 speakers in nine
 * languages; these are the English and Hindi ones offered in the app. Speaker numbers are the
 * model's own (k2-fsa's table for kokoro-multi-lang-v1_0).
 */
object NaturalVoices {
    val all = listOf(
        KokoroVoice(3, "Heart, American woman", "en"),
        KokoroVoice(2, "Bella, American woman", "en"),
        KokoroVoice(9, "Sarah, American woman", "en"),
        KokoroVoice(16, "Michael, American man", "en"),
        KokoroVoice(13, "Eric, American man", "en"),
        KokoroVoice(21, "Emma, British woman", "en"),
        KokoroVoice(26, "George, British man", "en"),
        KokoroVoice(31, "Alpha, Hindi woman", "hi"),
        KokoroVoice(32, "Beta, Hindi woman", "hi"),
        KokoroVoice(33, "Omega, Hindi man", "hi"),
        KokoroVoice(34, "Psi, Hindi man", "hi"),
    )

    val default: KokoroVoice = all.first()

    /**
     * Languages the natural voice is used for. Kokoro has Hindi voices, but its Hindi is read
     * through espeak-ng's rules rather than a word list, so Hindi waits for a listening test on
     * the phone before it joins; until then Hindi cards use the phone's own voice.
     */
    val languages = setOf("en")

    fun byId(id: Int): KokoroVoice? = all.firstOrNull { it.id == id }

    /**
     * The natural voice to say [text] with, or null to use the phone's own voice: when the natural
     * voice is off or not loaded, or the sentence is in a language it does not speak. The
     * language is the cards' [cardLanguage], except that text written in Devanagari is Hindi
     * whatever the cards are set to, so a Hindi sentence never reaches an English voice.
     * [chosen] is used for its own language; another language gets that language's first voice.
     */
    fun voiceFor(
        text: String,
        cardLanguage: String,
        chosen: KokoroVoice,
        on: Boolean,
        ready: Boolean,
        languages: Set<String> = this.languages,
    ): KokoroVoice? {
        if (!on || !ready) return null
        val language = if (writtenInDevanagari(text)) "hi" else cardLanguage
        if (language !in languages) return null
        return if (chosen.language == language) chosen else all.firstOrNull { it.language == language }
    }

    private fun writtenInDevanagari(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.count { it in 'ऀ'..'ॿ' } * 2 > letters.length
    }
}

/**
 * Audio made ahead of time for the cards on screen, so a chosen card plays at once. Keyed by the
 * text, the voice and the speed; the oldest entries go first once [maxItems] is reached.
 */
class AudioCache<A>(private val maxItems: Int = 24) {

    data class Key(val text: String, val voiceId: Int, val speed: Float)

    private val items = object : LinkedHashMap<Key, A>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, A>?) = size > maxItems
    }

    @Synchronized
    fun get(key: Key): A? = items[key]

    @Synchronized
    fun put(key: Key, audio: A) {
        items[key] = audio
    }

    @Synchronized
    fun contains(key: Key) = key in items

    /** Of [wanted], those not made yet, in the order given (the first card first). */
    @Synchronized
    fun missing(wanted: List<Key>): List<Key> = wanted.distinct().filter { it !in items }

    @Synchronized
    fun clear() = items.clear()

    val size: Int @Synchronized get() = items.size
}
