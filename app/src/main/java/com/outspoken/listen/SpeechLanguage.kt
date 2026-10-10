package com.outspoken.listen

/**
 * What to listen for: the main language's tags, best first, and the other languages the visitor
 * may switch to while talking (each its own list of tags, best first).
 */
data class SpeechWish(val main: List<String>, val others: List<List<String>> = emptyList())

/** How to listen with the speech packs the phone has. */
data class SpeechPlan(
    /** The language each session starts in. */
    val listenIn: String,
    /** Other languages with a pack on the phone that the recogniser may switch to. */
    val switchTo: List<String>,
    /** Packs to ask Android for: wanted ones not on the phone, or better than the one that is. */
    val download: List<String>,
    /** True when the main language has no pack on the phone yet, so [listenIn] is a stand-in. */
    val mainMissing: Boolean,
    /** True when [listenIn] has a working pack on the phone, so the on-device recogniser can use it. */
    val hasPack: Boolean,
)

/**
 * Listens in the best main-language tag with a pack [installed]; when there is none, in another
 * wanted language, else in any pack the phone has, so listening never stops for a missing pack.
 * The best tag of each language the phone [supported] can download is asked for when it is not
 * installed (en-IN while only en-US is there). Tags in [broken] failed on the phone and are skipped.
 */
fun planSpeech(
    wish: SpeechWish,
    installed: List<String>,
    supported: List<String>,
    broken: Set<String> = emptySet(),
): SpeechPlan {
    fun has(list: Collection<String>, tag: String) = list.any { same(it, tag) }
    val usable = installed.filterNot { has(broken, it) }
    val languages = listOf(wish.main) + wish.others
    val onPhone = languages.map { tags -> tags.firstOrNull { has(usable, it) } }
    val download = languages.mapNotNull { tags ->
        tags.firstOrNull { has(usable, it) || has(supported, it) }?.takeIf { !has(usable, it) && !has(broken, it) }
    }
    val listenIn = onPhone.filterNotNull().firstOrNull() ?: usable.firstOrNull() ?: wish.main.first()
    val switchTo = onPhone.filterNotNull().filterNot { same(it, listenIn) }.distinct()
    return SpeechPlan(listenIn, switchTo, download.distinct(), mainMissing = onPhone.first() == null, hasPack = has(usable, listenIn))
}

private fun same(a: String, b: String) = a.replace('_', '-').equals(b.replace('_', '-'), ignoreCase = true)

/** Android's speech recogniser error codes (`SpeechRecognizer.ERROR_*`) in words, for the log. */
fun errorName(code: Int): String = when (code) {
    1 -> "network timeout"
    2 -> "network"
    3 -> "audio"
    4 -> "server"
    5 -> "client"
    6 -> "no speech"
    7 -> "no match"
    8 -> "recogniser busy"
    9 -> "no permission"
    10 -> "too many requests"
    11 -> "server disconnected"
    12 -> "language not supported"
    13 -> "language pack missing"
    14 -> "cannot check support"
    15 -> "cannot follow download"
    else -> "error $code"
}

/** `SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_*` in words, for the log. */
fun confidenceName(level: Int): String = when (level) {
    1 -> "not sure"
    2 -> "fairly sure"
    3 -> "sure"
    else -> "sureness unknown"
}

/** `SpeechRecognizer.LANGUAGE_SWITCH_RESULT_*` in words, for the log; null when no switch was tried. */
fun switchName(result: Int): String? = when (result) {
    1 -> "switched"
    2 -> "switch failed"
    3 -> "no pack to switch to"
    else -> null
}
