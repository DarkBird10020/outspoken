package com.outspoken.stats

/**
 * Timings and counts for this session (PRD "Pit stats", shown on the stats screen for S1).
 * Memory only. The reply numbers are the latest reply's, not averages: averaged over a session
 * they hardly moved, and the screen looked frozen (owner's screenshot, 08:00).
 */
class PitStats(private val startedMs: Long) {

    private var askedAtMs: Long? = null
    private var lastReplyMs: Long? = null
    private var lastTokenRate: Float? = null
    private var answers = 0
    private var fallbacks = 0

    /** Reply cards the model has written, four per answer. */
    val repliesWritten get() = answers * REPLIES_PER_ANSWER

    val fallbackCount get() = fallbacks

    /** Decode speed of the latest model answer that gave one, or null when not measured. */
    val tokensPerSecond: Float? get() = lastTokenRate

    /**
     * While replies are being written, the time so far, counting up; otherwise the latest model
     * answer's time. Null before the first.
     */
    fun replyTimeSeconds(nowMs: Long): Float? =
        (askedAtMs?.let { (nowMs - it).coerceAtLeast(0) } ?: lastReplyMs)?.let { it / 1000f }

    /** Replies were asked for. A newer ask replaces one still being written. */
    fun onAsked(nowMs: Long) {
        askedAtMs = nowMs
    }

    fun onReplies(elapsedMs: Long, fromModel: Boolean, tokensPerSecond: Float?) {
        askedAtMs = null
        if (!fromModel) {
            fallbacks++
            return
        }
        answers++
        lastReplyMs = elapsedMs
        tokensPerSecond?.let { lastTokenRate = it }
    }

    fun sessionMillis(nowMs: Long) = (nowMs - startedMs).coerceAtLeast(0)

    private companion object {
        const val REPLIES_PER_ANSWER = 4
    }
}
