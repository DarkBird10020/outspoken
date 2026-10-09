package com.outspoken.log

import com.outspoken.eye.EyeSample
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Folds camera frames into one log line per [windowMs] instead of one per frame. The lowest
 * eye-open values show whether the eyes shut at any point inside the window.
 */
class EyeSummary(private val windowMs: Long = 1_000) {

    private var startMs: Long? = null
    private var frames = 0
    private var faceFrames = 0
    private var minLeft: Float? = null
    private var minRight: Float? = null
    private var lastFace: EyeSample? = null

    /** Adds a frame. Returns the previous window's line when this frame starts a new window. */
    fun add(sample: EyeSample): String? {
        val start = startMs
        val line = if (start != null && sample.timeMs - start >= windowMs) summary(sample.timeMs - start) else null
        if (start == null || line != null) reset(sample.timeMs)
        record(sample)
        return line
    }

    private fun reset(nowMs: Long) {
        startMs = nowMs
        frames = 0
        faceFrames = 0
        minLeft = null
        minRight = null
        lastFace = null
    }

    private fun record(sample: EyeSample) {
        frames++
        if (!sample.faceFound) return
        faceFrames++
        lastFace = sample
        sample.leftOpen?.let { minLeft = minOf(minLeft ?: it, it) }
        sample.rightOpen?.let { minRight = minOf(minRight ?: it, it) }
    }

    private fun summary(spanMs: Long): String = buildString {
        append("fps ").append(String.format(Locale.US, "%.1f", frames * 1000f / spanMs))
        append(", face ").append(faceFrames).append('/').append(frames)
        val face = lastFace ?: return@buildString
        append(", left ").append(open(face.leftOpen)).append(" min ").append(open(minLeft))
        append(", right ").append(open(face.rightOpen)).append(" min ").append(open(minRight))
        append(", yaw ").append(face.yawDeg.roundToInt()).append(", pitch ").append(face.pitchDeg.roundToInt())
    }

    private fun open(value: Float?) = value?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
}
