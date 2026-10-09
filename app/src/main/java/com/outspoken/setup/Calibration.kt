package com.outspoken.setup

import com.outspoken.eye.EyeSample

/**
 * About 25 seconds of spoken steps that measure this person's eyes in the phone's current
 * position (PRD F6), then set the look and blink lines from those numbers. Eye readings change a
 * lot with where the phone sits, so fixed lines that worked in one test failed in the next.
 */
class Calibration(private val stepMs: Long = 2_500, private val settleMs: Long = 700) {

    enum class Step(val prompt: String) {
        Rest("Look at the screen"),
        Up1("Look up, above the phone"),
        Rest2("Look at the screen"),
        Up2("Look up, above the phone"),
        Rest3("Look at the screen"),
        Close1("Close your eyes"),
        Open1("Open your eyes"),
        Close2("Close your eyes"),
        Done("Done"),
    }

    /** What was measured, all from samples after the first [settleMs] of each step. */
    data class Measured(
        val restGaze: Float,
        val restOpen: Float,
        val upReach: Float,
        val closedOpen: Float,
    )

    sealed interface Result {
        data class Ok(val tuning: Tuning, val measured: Measured) : Result
        data class Failed(val reason: String) : Result
    }

    private val seen = mutableMapOf<Step, MutableList<EyeSample>>()
    private var startMs = 0L

    var step = Step.Rest
        private set

    fun start(nowMs: Long) {
        startMs = nowMs
        step = Step.Rest
        seen.clear()
    }

    /** Share of the whole run done, 0 to 1. */
    fun progress(nowMs: Long): Float = ((nowMs - startMs).toFloat() / (stepMs * (Step.entries.size - 1))).coerceIn(0f, 1f)

    /** Feeds a frame. Returns the new step when it changes, so its prompt can be spoken. */
    fun onSample(sample: EyeSample): Step? {
        val index = ((sample.timeMs - startMs) / stepMs).toInt().coerceIn(0, Step.entries.size - 1)
        val current = Step.entries[index]
        val changed = current != step
        step = current
        val inStepMs = sample.timeMs - startMs - index * stepMs
        if (current != Step.Done && inStepMs >= settleMs && sample.faceFound) {
            seen.getOrPut(current) { mutableListOf() } += sample
        }
        return if (changed) current else null
    }

    fun result(current: Tuning): Result {
        val rests = listOf(Step.Rest, Step.Rest2, Step.Rest3).flatMap { seen[it].orEmpty() }
        if (rests.size < MIN_FRAMES) return Result.Failed("Face not seen while looking at the screen")
        val restGaze = median(rests.mapNotNull { it.gaze?.y }) ?: return Result.Failed("Eyes not read")
        val restOpen = median(rests.map { open(it) }) ?: return Result.Failed("Eyes not read")

        val reaches = listOf(Step.Up1, Step.Up2).map { up ->
            val frames = seen[up].orEmpty()
            if (frames.size < MIN_FRAMES) return Result.Failed("Face not seen while looking up")
            frames.mapNotNull { it.gaze?.y }.maxOfOrNull { restGaze - it } ?: 0f
        }
        val upReach = reaches.min()
        if (upReach < MIN_UP_REACH) return Result.Failed("Look up not seen; look higher above the phone")

        val depths = listOf(Step.Close1, Step.Close2).map { close ->
            val frames = seen[close].orEmpty()
            if (frames.size < MIN_FRAMES) return Result.Failed("Face not seen with the eyes closed")
            frames.minOf { open(it) }
        }
        val closedOpen = depths.max()
        val range = restOpen - closedOpen
        if (range < MIN_CLOSE_RANGE) return Result.Failed("Closed eyes not seen; close them fully")

        val measured = Measured(restGaze, restOpen, upReach, closedOpen)
        val tuning = current.copy(
            blink = current.blink.copy(
                closedBelow = closedOpen + range * SHUT_SHARE,
                openAbove = closedOpen + range * OPEN_SHARE,
            ),
            gaze = current.gaze.copy(lookStrength = upReach * LOOK_SHARE),
        )
        return Result.Ok(tuning, measured)
    }

    private fun open(sample: EyeSample) = minOf(sample.leftOpen ?: 0f, sample.rightOpen ?: 0f)

    private fun median(values: List<Float>): Float? =
        values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    companion object {
        const val MIN_FRAMES = 5
        const val MIN_UP_REACH = 0.1f
        const val MIN_CLOSE_RANGE = 0.12f

        /** A look counts at half of the smaller of the two measured looks up. */
        const val LOOK_SHARE = 0.5f

        /** Shut line halfway between closed and open; open line three quarters of the way up. */
        const val SHUT_SHARE = 0.5f
        const val OPEN_SHARE = 0.75f
    }
}
