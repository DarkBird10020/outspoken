package com.outspoken.hand

/**
 * Turns hand signs read frame by frame into one event per deliberate sign: the same sign, scored
 * at least [minScore], held for [holdMs], with gaps of no more than [gapMs] where the model saw
 * nothing (it drops single frames). A different sign starts the count again. After a sign fires,
 * nothing fires until no sign has been seen for [releaseMs], so a sign held on, or a hand lying
 * still in view, says its phrase once. Pure Kotlin, one thread, one clock.
 */
class GestureHold(
    private val holdMs: Long = 600,
    private val gapMs: Long = 250,
    private val releaseMs: Long = 500,
    private val minScore: Float = 0.6f,
) {
    private var current: HandSign? = null
    private var sinceMs = 0L
    private var lastSeenMs = 0L
    private var waitingRelease = false
    private var clearSinceMs: Long? = null

    /** Feed every reading, with null when no sign (or no hand) was seen. Returns a sign when it fires. */
    fun onFrame(sign: HandSign?, score: Float, timeMs: Long, allowed: Set<HandSign> = HandSign.entries.toSet()): HandSign? {
        val seen = sign?.takeIf { score >= minScore && it in allowed }
        if (waitingRelease) {
            if (seen != null) {
                clearSinceMs = null
                return null
            }
            val since = clearSinceMs ?: timeMs.also { clearSinceMs = it }
            if (timeMs - since >= releaseMs) waitingRelease = false
            return null
        }
        if (seen == null) {
            if (current != null && timeMs - lastSeenMs > gapMs) current = null
            return null
        }
        if (seen != current || timeMs - lastSeenMs > gapMs) {
            current = seen
            sinceMs = timeMs
        }
        lastSeenMs = timeMs
        if (timeMs - sinceMs < holdMs) return null
        current = null
        waitingRelease = true
        clearSinceMs = null
        return seen
    }
}
