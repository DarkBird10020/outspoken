package com.outspoken.listen

/** The language to listen in, and whether its speech pack must be downloaded first. */
data class LanguagePick(val tag: String, val download: Boolean)

/**
 * The first of [wanted] whose speech pack is [installed] on the phone; else the first the phone
 * [supported] can download (download = true); else the first wanted one, as it is.
 */
fun languageTag(wanted: List<String>, installed: List<String>, supported: List<String>): LanguagePick {
    fun has(list: List<String>, tag: String) = list.any { same(it, tag) }
    wanted.firstOrNull { has(installed, it) }?.let { return LanguagePick(it, download = false) }
    wanted.firstOrNull { has(supported, it) }?.let { return LanguagePick(it, download = true) }
    return LanguagePick(wanted.first(), download = false)
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
