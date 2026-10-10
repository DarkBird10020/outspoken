package com.outspoken.listen

/**
 * Decides which recognised speech is a real question from the visitor. The phone's own voice
 * comes back through the microphone, so text heard while it speaks, just after, or matching what
 * it said recently is dropped. All times share one clock.
 */
class HeardFilter(private val echoWindowMs: Long = 1_500) {

    private val recentlySaid = ArrayDeque<String>()
    private var speakingUntilMs: Long? = null
    private var speaking = false

    fun onSpeechStart(text: String) {
        speaking = true
        recentlySaid.addLast(normalise(text))
        while (recentlySaid.size > RECENT_SAID) recentlySaid.removeFirst()
    }

    fun onSpeechDone(nowMs: Long) {
        speaking = false
        speakingUntilMs = nowMs
    }

    /** The cleaned question, or null when it should be ignored. */
    fun accept(text: String, nowMs: Long): String? {
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        if (cleaned.length < MIN_CHARS) return null
        if (speaking) return null
        speakingUntilMs?.let { if (nowMs - it < echoWindowMs) return null }
        if (normalise(cleaned) in recentlySaid) return null
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    // Letters of any script, so Hindi echoes are caught too.
    private fun normalise(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val MIN_CHARS = 2
        const val RECENT_SAID = 5
    }
}
