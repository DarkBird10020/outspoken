package com.outspoken.scan

import com.outspoken.eye.Dot
import com.outspoken.log.EventLog
import java.util.Locale
import kotlin.math.abs

data class GazeSettings(
    /** How far the eyes must move up from where they rest to count as a look (0..1). */
    val lookStrength: Float = 0.45f,
    /** How far the eyes must move down from rest to count as a look; null turns looking down off. */
    val downStrength: Float? = 0.3f,
    /** How long a look must last before the highlight moves. 250 ms let quick glances step. */
    val lookHoldMs: Long = 350,
    /**
     * How far the iris must drop between the eye corners from rest to count as a look down
     * (`FaceMesh.irisDrop`). When set it replaces [downStrength]: with the phone below eye level
     * the "look down" blendshape had almost no room and moved with the lids.
     */
    val irisDownStrength: Float? = null,
)

enum class GazeStep { Next, Previous }

/**
 * Turns up and down eye movement into single steps: looking down moves the highlight down
 * ([GazeStep.Next]), looking up moves it up ([GazeStep.Previous]). Sideways looks are ignored.
 * One look is one step; the eyes come back to rest before the next. Looks are measured from the
 * resting gaze, which slowly follows posture, because the phone usually sits below eye level.
 *
 * Shut eyes never step. Looking down drops the upper lids, so the caller decides "shut" with the
 * lid gap check in the blink detector rather than the eye-open value alone, which read a look down
 * as a close on the phone.
 */
class GazeStepper(
    var settings: GazeSettings = GazeSettings(),
    private val log: EventLog = EventLog.None,
) {
    private var restY: Float? = null
    private var restIris: Float? = null
    private var looking: GazeStep? = null
    private var lookingSinceMs = 0L
    private var stepped = false
    private var smoothX: Float? = null
    private var smoothY: Float? = null
    private var smoothIris: Float? = null

    /** The smoothed gaze and iris drop the steps are decided on, for drawing a steady dot. */
    val smoothedGaze: Dot? get() = smoothY?.let { Dot(smoothX ?: 0f, it) }
    val smoothedIrisDrop: Float? get() = smoothIris
    private var lastStep: GazeStep? = null
    private var lastStepMs = Long.MIN_VALUE / 2

    /** Where the eyes rest up and down, the centre that looks are measured from; null until seen. */
    val restGaze: Float? get() = restY

    /** Where the iris rests between the eye corners; null until seen. */
    val restIrisDrop: Float? get() = restIris

    /**
     * Forget the resting point so it is taken again from the next frames. Used when the face comes
     * back after being lost: the person may now sit or hold the phone differently, and the old rest
     * made looks fire on their own or never reach the line.
     */
    fun forgetRest() {
        restY = null
        restIris = null
        looking = null
        stepped = false
        smoothX = null
        smoothY = null
        smoothIris = null
    }

    /** Starts from a measured resting gaze, for example the one calibration found. */
    fun restAt(y: Float, iris: Float? = null) {
        restY = y
        if (iris != null) restIris = iris
        looking = null
        stepped = false
    }

    /**
     * True when the iris points down far enough to be a look down, but not so far that the eyes
     * are shut: with the lids closed the iris reads far below any real look (phone log 00:42:18:
     * 0.31 against 0.01 to 0.08 for looks down), and counting that as a look blocked every blink.
     */
    fun irisLooksDown(irisY: Float?): Boolean {
        val line = settings.irisDownStrength ?: return false
        val rest = restIris ?: return false
        if (irisY == null) return false
        val drop = irisY - rest
        return drop >= line && drop < IRIS_SHUT_ABOVE
    }

    /**
     * [irisY] is [FaceMesh.irisDrop]. Shut eyes never step: on the phone a real close read an iris
     * drop of 0.01 to 0.03, inside the look down range, so letting the iris override "shut" made
     * blinks step down. The lid gap check already keeps a look down from reading as shut (looks
     * down 0.15 to 0.24, closes 0.07 to 0.10).
     */
    fun onSample(gaze: Dot?, eyesOpen: Boolean, timeMs: Long, irisY: Float? = null): GazeStep? {
        val irisLine = settings.irisDownStrength
        if (gaze == null || !eyesOpen) {
            // Start the averages afresh when the eyes open again: a wink or blink shifts the
            // readings, and averaging back from them read as a look the other way.
            looking = null
            smoothX = null
            smoothY = null
            smoothIris = null
            return null
        }
        // Running averages of both readings: on the phone single-frame wobble kept crossing the
        // up line (0.30 to 0.34 against 0.28, a step every few seconds). About 0.1 s of lag.
        val iris = irisY?.let { smooth(smoothIris, it).also { v -> smoothIris = v } }
        val gazeY = smooth(smoothY, gaze.y).also { smoothY = it }
        smoothX = smooth(smoothX, gaze.x)
        if (iris != null && restIris == null) restIris = iris
        val irisDown = irisLooksDown(iris)
        val y = gazeY
        val centre = restY ?: y.also { restY = it }
        val dy = y - centre
        val down = settings.downStrength
        val direction = when {
            -dy >= settings.lookStrength -> GazeStep.Previous
            // Iris when it is read; the blendshape look down only when this frame has no iris.
            irisLine != null && iris != null -> if (irisDown) GazeStep.Next else null
            down != null && dy >= down -> GazeStep.Next
            else -> null
        }

        if (direction == null) {
            if (abs(dy) < restBand()) restY = centre + dy * REST_FOLLOW
            // The resting iris drifts by about 0.04 while the eyes rest (phone log 00:51). Follow it
            // across the whole band below the line; following only within half the line left the
            // rest behind and resting eyes read as a look down again and again.
            val rest = restIris
            if (irisLine != null && iris != null && rest != null && abs(iris - rest) < irisLine * IRIS_REST_BAND) {
                restIris = rest + (iris - rest) * IRIS_REST_FOLLOW
            }
            looking = null
            stepped = false
            return null
        }
        // The eyes coming back from a look pass rest and read as a short look the other way; on the
        // phone every look down was undone by an "up" about half a second later.
        if (direction != lastStep && timeMs - lastStepMs < REBOUND_MS) {
            looking = null
            return null
        }
        if (direction != looking) {
            looking = direction
            lookingSinceMs = timeMs
            stepped = false
            return null
        }
        val heldMs = timeMs - lookingSinceMs
        // The minimum gap only delays a new step; it must not reset a look that already stepped,
        // or holding a look would step again every gap.
        if (stepped || heldMs < settings.lookHoldMs || timeMs - lastStepMs < MIN_STEP_GAP_MS) return null
        stepped = true
        lastStep = direction
        lastStepMs = timeMs
        val words = when {
            direction == GazeStep.Previous -> "up ${format(-dy)} held $heldMs ms -> previous"
            irisLine != null -> "down (iris ${format((iris ?: 0f) - (restIris ?: 0f))}) held $heldMs ms -> next"
            else -> "down ${format(dy)} held $heldMs ms -> next"
        }
        log.write("gaze", "look $words")
        return direction
    }

    private fun smooth(previous: Float?, value: Float) = previous?.let { it + SMOOTHING * (value - it) } ?: value

    /** Gaze this close to rest counts as resting and slowly moves the rest point. */
    private fun restBand() = minOf(settings.lookStrength, settings.downStrength ?: settings.lookStrength) / 2

    private fun format(value: Float) = String.format(Locale.US, "%.2f", value)

    private companion object {
        /** Share of the way the resting gaze moves toward the current one per frame at rest. */
        const val REST_FOLLOW = 0.05f

        /** After a step, a look the other way this soon is the eyes coming back, not a new look. */
        const val REBOUND_MS = 700L

        /** Weight of the newest frame in the running average of the gaze readings. */
        const val SMOOTHING = 0.35f

        /** No two steps closer than this, so the highlight never jitters. */
        const val MIN_STEP_GAP_MS = 600L

        /**
         * The iris rest follows only within this share of the line, and gently. Following during
         * looks (1% a frame) moved the rest toward each look down, so after several looks down in
         * a row they stopped reaching the line (owner report).
         */
        const val IRIS_REST_BAND = 0.75f
        const val IRIS_REST_FOLLOW = 0.03f

        /** Iris drop beyond this is shut eyes, not a look down. */
        const val IRIS_SHUT_ABOVE = 0.15f
    }
}
