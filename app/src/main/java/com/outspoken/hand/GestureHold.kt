package com.outspoken.hand

/** A sign that fired, with how long it was held and how steady the readings were, for the log. */
data class HeldSign(val sign: HandSign, val heldMs: Long, val steadyReadings: Int, val readings: Int)

/**
 * Turns hand signs read frame by frame into one event per deliberate sign. A sign fires once it
 * has been held for [holdMs] ([chooseHoldMs] for the fist, which says the lit card) and at least
 * [minShare] of the readings over that time were that sign. Readings of nothing, or of another
 * sign, for up to [gapMs] do not break a hold: on the phone the model flipped between pointing
 * up and a fist while a finger was raised (15:33:13 to 15:33:28: Pointing_Up 15, Closed_Fist 9),
 * and the fist fired after 0.6 s of it, saying "I want some water, please." (15:33:17).
 *
 * After a sign fires it cannot fire again until no sign has been seen for [releaseMs], so a sign
 * held on, or a hand lying still in view, says its phrase once. Another sign can follow at once,
 * without the hand being lowered (on the phone the pointing finger waited 12.5 s, 15:33:17 to
 * 15:33:30). Pure Kotlin, one thread, one clock.
 */
class GestureHold(
    private val holdMs: Long = 600,
    private val chooseHoldMs: Long = 1_000,
    private val gapMs: Long = 250,
    private val releaseMs: Long = 500,
    private val minScore: Float = 0.6f,
    private val minShare: Float = 0.7f,
) {
    private val readings = ArrayDeque<Pair<Long, HandSign?>>()
    private var current: HandSign? = null
    private var sinceMs = 0L
    private var lastSeenMs = 0L
    private var blocked: HandSign? = null
    private var clearSinceMs: Long? = null

    /** Feed every reading, with null when no sign (or no hand) was seen. Returns a sign when it fires. */
    fun onFrame(sign: HandSign?, score: Float, timeMs: Long, allowed: Set<HandSign> = HandSign.entries.toSet()): HeldSign? {
        val seen = sign?.takeIf { score >= minScore && it in allowed }
        if (seen == null) {
            val since = clearSinceMs ?: timeMs.also { clearSinceMs = it }
            if (timeMs - since >= releaseMs) blocked = null
        } else {
            clearSinceMs = null
        }
        readings.addLast(timeMs to seen)
        while (readings.first().first <= timeMs - maxOf(holdMs, chooseHoldMs)) readings.removeFirst()

        val candidate = seen?.takeIf { it != blocked }
        if (current != null && timeMs - lastSeenMs > gapMs) current = null
        if (current == null && candidate != null) {
            current = candidate
            sinceMs = timeMs
        }
        val held = current ?: return null
        if (candidate != held) return null
        lastSeenMs = timeMs
        val hold = holdFor(held)
        if (timeMs - sinceMs < hold) return null
        val window = readings.filter { it.first > timeMs - hold }
        val steady = window.count { it.second == held }
        if (steady < minShare * window.size) return null
        current = null
        blocked = held
        return HeldSign(held, timeMs - sinceMs, steady, window.size)
    }

    private fun holdFor(sign: HandSign) = if (sign.action == HandAction.ChooseLit) chooseHoldMs else holdMs
}
