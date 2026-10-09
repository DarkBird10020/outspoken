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
    /** How long a look must last before the highlight moves. */
    val lookHoldMs: Long = 250,
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
    private var looking: GazeStep? = null
    private var lookingSinceMs = 0L
    private var stepped = false

    /** Where the eyes rest up and down, the centre that looks are measured from; null until seen. */
    val restGaze: Float? get() = restY

    /** Starts from a measured resting gaze, for example the one calibration found. */
    fun restAt(y: Float) {
        restY = y
        looking = null
        stepped = false
    }

    fun onSample(gaze: Dot?, eyesOpen: Boolean, timeMs: Long): GazeStep? {
        if (gaze == null || !eyesOpen) {
            looking = null
            return null
        }
        val centre = restY ?: gaze.y.also { restY = it }
        val dy = gaze.y - centre
        val down = settings.downStrength
        val direction = when {
            -dy >= settings.lookStrength && gaze.y < LOOK_UP_MAX_Y -> GazeStep.Previous
            down != null && dy >= down -> GazeStep.Next
            else -> null
        }

        if (direction == null) {
            if (abs(dy) < restBand()) restY = centre + dy * REST_FOLLOW
            looking = null
            stepped = false
            return null
        }
        if (direction != looking) {
            looking = direction
            lookingSinceMs = timeMs
            stepped = false
            return null
        }
        val heldMs = timeMs - lookingSinceMs
        if (heldMs >= REST_RESET_MS) {
            // Looking one way this long is a new posture, not a look.
            log.write("gaze", "new resting gaze ${format(gaze.y)}")
            restY = gaze.y
            looking = null
            stepped = false
            return null
        }
        if (stepped || heldMs < settings.lookHoldMs) return null
        stepped = true
        val words = if (direction == GazeStep.Next) "down ${format(dy)} held $heldMs ms -> next" else "up ${format(-dy)} held $heldMs ms -> previous"
        log.write("gaze", "look $words")
        return direction
    }

    /** Gaze this close to rest counts as resting and slowly moves the rest point. */
    private fun restBand() = minOf(settings.lookStrength, settings.downStrength ?: settings.lookStrength) / 2

    private fun format(value: Float) = String.format(Locale.US, "%.2f", value)

    private companion object {
        /** Share of the way the resting gaze moves toward the current one per frame at rest. */
        const val REST_FOLLOW = 0.05f
        const val REST_RESET_MS = 3_000L
        /** Eyes must look toward or above the top bezel, not just glance within the screen cards. */
        const val LOOK_UP_MAX_Y = 0.20f
    }
}
