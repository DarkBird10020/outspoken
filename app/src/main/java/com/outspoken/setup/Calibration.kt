package com.outspoken.setup

import com.outspoken.eye.EyeSample

/**
 * About 30 seconds of spoken steps that measure this person's eyes in the phone's current
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
        Down1("Look down, below the phone"),
        Rest4("Look at the screen"),
        Down2("Look down, below the phone"),
        Rest5("Look at the screen"),
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
        /** How far the eyes moved down from rest; 0 when no look down was seen. */
        val downReach: Float = 0f,
        /** Iris drop between the eye corners at rest and how far it dropped looking down; null if not read. */
        val restIris: Float? = null,
        val irisDownReach: Float? = null,
        /** Lid gap (`eyeShape`, the wider eye) looking at the screen and with the eyes closed; null if not read. */
        val restGap: Float? = null,
        val closedGap: Float? = null,
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
        val rests = listOf(Step.Rest, Step.Rest2, Step.Rest3, Step.Rest4, Step.Rest5).flatMap { seen[it].orEmpty() }
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

        val restIris = median(rests.mapNotNull { it.irisY })
        val irisDownReach = restIris?.let { rest ->
            listOf(Step.Down1, Step.Down2).minOf { down ->
                seen[down].orEmpty().mapNotNull { it.irisY }.maxOfOrNull { it - rest } ?: 0f
            }
        }

        // A look down that is not seen only turns looking down off; up still moves.
        val downReach = listOf(Step.Down1, Step.Down2).minOf { down ->
            seen[down].orEmpty().mapNotNull { it.gaze?.y }.maxOfOrNull { it - restGaze } ?: 0f
        }

        val depths = listOf(Step.Close1, Step.Close2).map { close ->
            val frames = seen[close].orEmpty()
            if (frames.size < MIN_FRAMES) return Result.Failed("Face not seen with the eyes closed")
            frames.minOf { open(it) }
        }
        // The deeper of the two closes. Taking the shallower let one weak or late close set the
        // lines almost at open: the 00:13 phone run measured closed 0.54 against open 0.88 and a
        // lid gap of 0.22 against 0.31, and normal looking then chose "I need water" by itself.
        val closedOpen = depths.min()
        val range = restOpen - closedOpen
        if (range < MIN_CLOSE_RANGE) return Result.Failed("Closed eyes not seen; close them fully")

        val restGap = median(rests.mapNotNull { gap(it) })
        val closedGaps = listOf(Step.Close1, Step.Close2).map { close -> seen[close].orEmpty().mapNotNull { gap(it) }.minOrNull() }
        val closedGap = if (closedGaps.any { it == null }) null else closedGaps.minOf { it!! }
        val gapRange = if (restGap != null && closedGap != null) restGap - closedGap else null
        val useGap = gapRange != null && gapRange >= MIN_GAP_RANGE

        val measured = Measured(restGaze, restOpen, upReach, closedOpen, downReach, restIris, irisDownReach, restGap, closedGap)
        val tuning = current.copy(
            blink = current.blink.copy(
                closedBelow = closedOpen + range * SHUT_SHARE,
                openAbove = closedOpen + range * OPEN_SHARE,
                shapeClosedBelow = if (useGap) closedGap!! + gapRange!! * GAP_SHUT_SHARE else null,
                shapeOpenAbove = if (useGap) closedGap!! + gapRange!! * GAP_OPEN_SHARE else null,
            ),
            gaze = current.gaze.copy(
                lookStrength = upReach * LOOK_SHARE,
                downStrength = if (downReach >= MIN_DOWN_REACH) downReach * DOWN_SHARE else null,
                irisDownStrength = irisDownReach?.takeIf { it >= MIN_IRIS_DOWN_REACH }?.let { maxOf(it * DOWN_SHARE, MIN_IRIS_DOWN_LINE) },
            ),
        )
        return Result.Ok(tuning, measured)
    }

    private fun open(sample: EyeSample) = minOf(sample.leftOpen ?: 0f, sample.rightOpen ?: 0f)

    /** The wider of the two lid gaps, since both must be below the line to count as shut. */
    private fun gap(sample: EyeSample): Float? {
        val dots = sample.dots ?: return null
        return maxOf(dots.leftShape ?: return null, dots.rightShape ?: return null)
    }

    private fun median(values: List<Float>): Float? =
        values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    companion object {
        const val MIN_FRAMES = 5
        const val MIN_UP_REACH = 0.1f
        const val MIN_DOWN_REACH = 0.1f

        /** Iris drop as a share of the eye width; a deliberate look down moves it several times this. */
        const val MIN_IRIS_DOWN_REACH = 0.02f

        /**
         * Floor for the iris look down line. 0.02 was two thirds of the owner's whole downward
         * range; with the rest point following drift and the readings smoothed, 0.01 is enough.
         */
        const val MIN_IRIS_DOWN_LINE = 0.01f
        const val MIN_CLOSE_RANGE = 0.12f

        /** A look counts at half of the smaller of the two measured looks up. */
        const val LOOK_SHARE = 0.5f

        /**
         * A look down counts at 30% of the measured one. The eyes have much less room to move down
         * than up (owner: "less area to push it down"; phone runs: iris drop about 0.03 down
         * against gaze 0.5 to 0.6 up), so down takes a smaller share of its own range than up.
         */
        const val DOWN_SHARE = 0.3f

        /** Shut line halfway between closed and open; open line three quarters of the way up. */
        const val SHUT_SHARE = 0.5f
        const val OPEN_SHARE = 0.75f

        /**
         * Lid gap lines sit just above the measured close. Phone run at 23:38: closed 0.12, open
         * 0.30, and looking down at the screen read 0.14 to 0.17, so a shut line 30% of the way up
         * (0.17) let looking down count as closed. 10% gives 0.14.
         */
        const val GAP_SHUT_SHARE = 0.1f
        const val GAP_OPEN_SHARE = 0.3f
        const val MIN_GAP_RANGE = 0.08f
    }
}
