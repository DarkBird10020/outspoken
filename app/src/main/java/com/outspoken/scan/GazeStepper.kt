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
    private var lastStep: GazeStep? = null
    private var lastStepMs = Long.MIN_VALUE / 2

    /** Where the eyes rest up and down, the centre that looks are measured from; null until seen. */
    val restGaze: Float? get() = restY

    /** Where the iris rests between the eye corners; null until seen. */
    val restIrisDrop: Float? get() = restIris

    /** Starts from a measured resting gaze, for example the one calibration found. */
    fun restAt(y: Float, iris: Float? = null) {
        restY = y
        if (iris != null) restIris = iris
        looking = null
        stepped = false
    }

    /** True when the iris points down far enough to be a look down rather than anything else. */
    fun irisLooksDown(irisY: Float?): Boolean {
        val line = settings.irisDownStrength ?: return false
        val rest = restIris ?: return false
        return irisY != null && irisY - rest >= line
    }

    /**
     * [irisY] is [FaceMesh.irisDrop]. In iris mode a look down counts even while the lids read as
     * shut, since looking down drops them; the blink detector is told not to start a close then.
     */
    fun onSample(gaze: Dot?, eyesOpen: Boolean, timeMs: Long, irisY: Float? = null): GazeStep? {
        val irisLine = settings.irisDownStrength
        if (irisY != null && restIris == null) restIris = irisY
        val irisDown = irisLooksDown(irisY)
        if (gaze == null || (!eyesOpen && !irisDown)) {
            looking = null
            return null
        }
        val centre = restY ?: gaze.y.also { restY = it }
        val dy = gaze.y - centre
        val down = settings.downStrength
        val direction = when {
            eyesOpen && -dy >= settings.lookStrength -> GazeStep.Previous
            irisLine != null -> if (irisDown) GazeStep.Next else null
            down != null && dy >= down -> GazeStep.Next
            else -> null
        }

        if (direction == null) {
            if (abs(dy) < restBand()) restY = centre + dy * REST_FOLLOW
            val rest = restIris
            if (irisLine != null && irisY != null && rest != null && abs(irisY - rest) < irisLine / 2) {
                restIris = rest + (irisY - rest) * REST_FOLLOW
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
        if (stepped || heldMs < settings.lookHoldMs) return null
        stepped = true
        lastStep = direction
        lastStepMs = timeMs
        val words = when {
            direction == GazeStep.Previous -> "up ${format(-dy)} held $heldMs ms -> previous"
            irisLine != null -> "down (iris ${format((irisY ?: 0f) - (restIris ?: 0f))}) held $heldMs ms -> next"
            else -> "down ${format(dy)} held $heldMs ms -> next"
        }
        log.write("gaze", "look $words")
        return direction
    }

    /** Gaze this close to rest counts as resting and slowly moves the rest point. */
    private fun restBand() = minOf(settings.lookStrength, settings.downStrength ?: settings.lookStrength) / 2

    private fun format(value: Float) = String.format(Locale.US, "%.2f", value)

    private companion object {
        /** Share of the way the resting gaze moves toward the current one per frame at rest. */
        const val REST_FOLLOW = 0.05f

        /** After a step, a look the other way this soon is the eyes coming back, not a new look. */
        const val REBOUND_MS = 700L
    }
}
