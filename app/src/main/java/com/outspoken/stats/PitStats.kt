package com.outspoken.stats

/**
 * Timings and counts for this session (PRD "Pit stats", shown on the stats screen for S1).
 * Memory only.
 */
class PitStats(private val startedMs: Long) {

    private val replyTimesMs = mutableListOf<Long>()
    private val tokenRates = mutableListOf<Float>()
    private var fallbacks = 0

    /** Average time from asking to four model replies ready, or null before the first. */
    val replyTimeSeconds: Float? get() = replyTimesMs.takeIf { it.isNotEmpty() }?.average()?.let { it.toFloat() / 1000f }

    /** Average decode speed of the model, or null when not measured. */
    val tokensPerSecond: Float? get() = tokenRates.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    /** Reply cards the model has written, four per answer. */
    val repliesWritten get() = replyTimesMs.size * REPLIES_PER_ANSWER

    val fallbackCount get() = fallbacks

    fun onReplies(elapsedMs: Long, fromModel: Boolean, tokensPerSecond: Float?) {
        if (!fromModel) {
            fallbacks++
            return
        }
        replyTimesMs += elapsedMs
        tokensPerSecond?.let { tokenRates += it }
    }

    fun sessionMillis(nowMs: Long) = (nowMs - startedMs).coerceAtLeast(0)

    private companion object {
        const val REPLIES_PER_ANSWER = 4
    }
}
