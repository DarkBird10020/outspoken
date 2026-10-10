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
    private var written = 0
    private var fallbacks = 0

    /**
     * Reply cards the model has written. Phrase bank cards that topped up a short answer are not
     * counted: four per answer was counted before, whatever the model gave.
     */
    val repliesWritten get() = written

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

    /** [modelReplies] is how many of the cards the model wrote. */
    fun onReplies(elapsedMs: Long, fromModel: Boolean, tokensPerSecond: Float?, modelReplies: Int) {
        askedAtMs = null
        if (!fromModel) {
            fallbacks++
            return
        }
        written += modelReplies
        lastReplyMs = elapsedMs
        tokensPerSecond?.let { lastTokenRate = it }
    }

    fun sessionMillis(nowMs: Long) = (nowMs - startedMs).coerceAtLeast(0)
}
