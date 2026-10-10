package com.outspoken.setup

import com.outspoken.eye.EyeSample
import com.outspoken.eye.FaceDots

/**
 * About 30 seconds of spoken steps that measure this person's eyes in the phone's current
 * position (PRD F6), then set the look and blink lines from those numbers. Eye readings change a
 * lot with where the phone sits, so fixed lines that worked in one test failed in the next.
 */
class Calibration(private val stepMs: Long = 2_500, private val settleMs: Long = 700) {

    /** [stage] is the step as shown on screen, 1 to [STAGES]: eyes at rest, look up, look down, closed eyes, done. */
    enum class Step(val prompt: String, val stage: Int) {
        Rest("Look at the screen", 1),
        Up1("Look up, above the phone", 2),
        Rest2("Look at the screen", 2),
        Up2("Look up, above the phone", 2),
        Rest3("Look at the screen", 3),
        Down1("Look down, below the phone", 3),
        Rest4("Look at the screen", 3),
        Down2("Look down, below the phone", 3),
        Rest5("Look at the screen", 4),
        Close1("Close your eyes", 4),
        Open1("Open your eyes", 4),
        Close2("Close your eyes", 4),
        Done("Done", 5),
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
        /** Each eye's own lid gaps; null if not read. */
        val leftGaps: EyeGaps? = null,
        val rightGaps: EyeGaps? = null,
    )

    /**
     * One eye's lid gap looking at the screen, closed, and looking down (null when no look down
     * was seen). The gap has to tell a close from a look down, so with a look down measured the
     * shut line sits halfway between the two and the open line three quarters of the way to the
     * look down. On the phone with glasses (17:18) the old line, just above the calibration close
     * (0.07 -> 0.10), missed the everyday closes of 0.10 to 0.14, while looking down read 0.18 to
     * 0.22 (left eye). Without a look down the lines stay just above the close, as before.
     */
    data class EyeGaps(val rest: Float, val closed: Float, val down: Float?) {
        val usable: Boolean get() = rest - closed >= MIN_GAP_RANGE

        val shutLine: Float
            get() = if (down != null) closed + (down - closed) * DOWN_GAP_SHUT_SHARE else closed + (rest - closed) * GAP_SHUT_SHARE

        val openLine: Float
            get() = if (down != null) closed + (down - closed) * DOWN_GAP_OPEN_SHARE else closed + (rest - closed) * GAP_OPEN_SHARE
    }

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
    /** How long the whole calibration takes, from the first step to Done. */
    val totalMs: Long get() = stepMs * (Step.entries.size - 1)

    fun progress(nowMs: Long): Float = ((nowMs - startMs).toFloat() / totalMs).coerceIn(0f, 1f)

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
        // One good look up is enough. Requiring both made the whole calibration fail on the phone
        // ("Look up not seen") and left the blink lines unset; now a missed look up only keeps the
        // current look up line.
        val upReach = reaches.max()
        val upSeen = upReach >= MIN_UP_REACH

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

        val shutLine = closedOpen + range * SHUT_SHARE
        // Frames clearly looking down: at least half way to the smaller of the two looks.
        val lookingDown = if (downReach >= MIN_DOWN_REACH) {
            listOf(Step.Down1, Step.Down2).flatMap { seen[it].orEmpty() }
                .filter { frame -> frame.gaze?.let { it.y - restGaze >= downReach * DOWN_FRAME_SHARE } == true }
        } else {
            emptyList()
        }
        val left = eyeGaps(rests, shutLine, lookingDown) { it.leftShape }
        val right = eyeGaps(rests, shutLine, lookingDown) { it.rightShape }
        val useGap = left?.usable == true && right?.usable == true

        val measured = Measured(
            restGaze, restOpen, upReach, closedOpen, downReach, restIris, irisDownReach,
            restGap = if (left != null && right != null) maxOf(left.rest, right.rest) else null,
            closedGap = if (left != null && right != null) maxOf(left.closed, right.closed) else null,
            leftGaps = left,
            rightGaps = right,
        )
        val tuning = current.copy(
            blink = current.blink.copy(
                closedBelow = shutLine,
                openAbove = closedOpen + range * OPEN_SHARE,
                shapeClosedBelow = if (useGap) left!!.shutLine else null,
                shapeOpenAbove = if (useGap) left!!.openLine else null,
                rightShapeClosedBelow = if (useGap) right!!.shutLine else null,
                rightShapeOpenAbove = if (useGap) right!!.openLine else null,
            ),
            gaze = current.gaze.copy(
                lookStrength = if (upSeen) maxOf(upReach * LOOK_SHARE, MIN_UP_LINE) else current.gaze.lookStrength,
                downStrength = if (downReach >= MIN_DOWN_REACH) downReach * DOWN_SHARE else null,
                irisDownStrength = irisDownReach?.takeIf { it >= MIN_IRIS_DOWN_REACH }?.let { maxOf(it * DOWN_SHARE, MIN_IRIS_DOWN_LINE) },
            ),
        )
        return Result.Ok(tuning, measured)
    }

    private fun open(sample: EyeSample) = minOf(sample.leftOpen ?: 0f, sample.rightOpen ?: 0f)

    /**
     * One eye's gaps, or null if not read. Closed is the typical gap while the eyes read shut, in
     * the deeper of the two closes. The single deepest frame (a hard squeeze reads 0.00 to 0.03)
     * set a line of 0.09 that held closes of 0.10 to 0.15 missed (05:04 run). The median of the
     * whole step took in the open frames before the person closed, as "Close your eyes" takes
     * 1.3 s to say: closed 0.28 against open 0.29 turned the gap check off, and open eyes looking
     * down picked cards (05:36 run). A close with no shut frames (half done) leaves the other.
     */
    private fun eyeGaps(rests: List<EyeSample>, shutLine: Float, lookingDown: List<EyeSample>, gapOf: (FaceDots) -> Float?): EyeGaps? {
        fun gaps(frames: List<EyeSample>) = frames.mapNotNull { frame -> frame.dots?.let(gapOf) }
        val rest = median(gaps(rests)) ?: return null
        val closed = listOf(Step.Close1, Step.Close2).mapNotNull { close ->
            median(gaps(seen[close].orEmpty().filter { open(it) < shutLine }))
        }.minOrNull() ?: return null
        val down = median(gaps(lookingDown))?.takeIf { it > closed }
        return EyeGaps(rest, closed, down)
    }

    private fun median(values: List<Float>): Float? =
        values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    companion object {
        /** Steps shown on screen ("Step 2 of 5"). */
        const val STAGES = 5

        const val MIN_FRAMES = 5
        const val MIN_UP_REACH = 0.1f

        /**
         * Floor for the look up line. At 0.15 (02:14 phone run) resting wobble of 0.16 to 0.24 read
         * as looks up and undid looks down; real looks up measured 0.4 to 0.7.
         */
        const val MIN_UP_LINE = 0.2f
        const val MIN_DOWN_REACH = 0.1f

        /** Iris drop as a share of the eye width; a deliberate look down moves it several times this. */
        const val MIN_IRIS_DOWN_REACH = 0.02f

        /**
         * Floor for the iris look down line. 0.02 was two thirds of the owner's whole downward
         * range; with the rest point following drift and the readings smoothed, 0.01 is enough.
         */
        const val MIN_IRIS_DOWN_LINE = 0.01f
        const val MIN_CLOSE_RANGE = 0.12f

        /** A look up counts at 35% of the smaller measured look up (half needed too far a look). */
        const val LOOK_SHARE = 0.35f

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

        /** With a look down measured: shut line halfway from closed to looking down, open line three quarters. */
        const val DOWN_GAP_SHUT_SHARE = 0.5f
        const val DOWN_GAP_OPEN_SHARE = 0.75f

        /** A frame counts as looking down once the gaze is this share of the way to the smaller look down. */
        const val DOWN_FRAME_SHARE = 0.5f
    }
}
