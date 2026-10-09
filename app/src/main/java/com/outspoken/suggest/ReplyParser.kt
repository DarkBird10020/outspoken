package com.outspoken.suggest

const val REPLY_COUNT = 4

/** PRD asks for under about eight words; a little slack avoids needless retries. */
const val MAX_REPLY_WORDS = 10

/**
 * Pulls exactly four distinct short replies out of model output, or returns null so the caller
 * can retry. Tolerates text or code fences around the list and a trailing comma inside it.
 */
fun parseReplies(output: String): List<String>? {
    val start = output.indexOf('[')
    val end = output.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    val replies = parseStringList(output.substring(start, end + 1))?.map { it.trim() } ?: return null
    if (replies.size != REPLY_COUNT) return null
    if (replies.any { it.isEmpty() || it.split(Regex("\\s+")).size > MAX_REPLY_WORDS }) return null
    if (replies.map { it.lowercase() }.toSet().size != REPLY_COUNT) return null
    return replies
}

/** Parses a JSON array of strings. Returns null for anything else. */
fun parseStringList(json: String): List<String>? {
    var i = 0
    fun skipSpace() {
        while (i < json.length && json[i].isWhitespace()) i++
    }

    skipSpace()
    if (json.getOrNull(i) != '[') return null
    i++
    val items = mutableListOf<String>()
    while (true) {
        skipSpace()
        if (json.getOrNull(i) == ']') {
            i++
            break
        }
        if (json.getOrNull(i) != '"') return null
        i++
        val item = StringBuilder()
        while (true) {
            val c = json.getOrNull(i++) ?: return null
            if (c == '"') break
            if (c != '\\') {
                item.append(c)
                continue
            }
            when (val escaped = json.getOrNull(i++) ?: return null) {
                '"', '\\', '/' -> item.append(escaped)
                'n', 't', 'r' -> item.append(' ')
                'b', 'f' -> Unit
                'u' -> {
                    val code = json.substring(i, minOf(i + 4, json.length)).takeIf { it.length == 4 }?.toIntOrNull(16)
                        ?: return null
                    item.append(code.toChar())
                    i += 4
                }
                else -> return null
            }
        }
        items += item.toString()
        skipSpace()
        when (json.getOrNull(i)) {
            ',' -> i++
            ']' -> Unit
            else -> return null
        }
    }
    skipSpace()
    return if (i == json.length) items else null
}
