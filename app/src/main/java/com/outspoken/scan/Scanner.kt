package com.outspoken.scan

/**
 * Moves a highlight over [cards] (card ids), one step every [intervalMs]. Time stops while
 * paused. Pure timing so it can be tested without a clock.
 */
class Scanner(var intervalMs: Long = 1200) {

    var cards: List<Int> = emptyList()
        private set

    private var startMs = 0L
    private var pausedAtMs: Long? = null

    val paused get() = pausedAtMs != null

    /** Starts again from the first card. */
    fun restart(cards: List<Int>, nowMs: Long) {
        this.cards = cards
        startMs = nowMs
        if (pausedAtMs != null) pausedAtMs = nowMs
    }

    fun pause(nowMs: Long) {
        if (pausedAtMs == null) pausedAtMs = nowMs
    }

    fun resume(nowMs: Long) {
        pausedAtMs?.let { startMs += nowMs - it }
        pausedAtMs = null
    }

    /**
     * The card highlighted at [timeMs], or null when there are no cards or the time is from
     * before the last restart (a blink that began before the cards changed).
     */
    fun cardAt(timeMs: Long): Int? {
        if (cards.isEmpty() || timeMs < startMs) return null
        val time = pausedAtMs?.let { minOf(timeMs, it) } ?: timeMs
        val step = (time - startMs).coerceAtLeast(0) / intervalMs
        return cards[(step % cards.size).toInt()]
    }
}
