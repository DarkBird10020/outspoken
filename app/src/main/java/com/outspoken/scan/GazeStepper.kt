package com.outspoken.scan

import com.outspoken.eye.Dot
import com.outspoken.log.EventLog
import java.util.Locale
import kotlin.math.abs

data class GazeSettings(
    /** How far the eyes must move up or down from where they rest to count as a look (0..1). */
    val lookStrength: Float = 0.25f,
    /** How long a look must last before the highlight moves. */
    val lookHoldMs: Long = 250,
)

enum class GazeStep { Next, Previous }

/**
 * Turns up and down eye movement into single steps: looking down moves to the next card, up to
 * the previous one. Sideways looks are ignored. One look is one step; the eyes must come back to
 * rest before the next. Looks are measured from the resting gaze, which slowly follows posture,
 * because the phone sits below eye level and "straight at the phone" is not the gaze centre.
 */
class GazeStepper(
    var settings: GazeSettings = GazeSettings(),
    private val log: EventLog = EventLog.None,
) {
    private var restY: Float? = null
    private var looking: GazeStep? = null
    private var lookingSinceMs = 0L
    private var stepped = false

    /** [eyesOpen] false while the eyes are closing or shut: lids dropping reads as looking down. */
    fun onSample(gaze: Dot?, eyesOpen: Boolean, timeMs: Long): GazeStep? {
        if (gaze == null || !eyesOpen) {
            looking = null
            return null
        }
        val centre = restY ?: gaze.y.also { restY = it }
        val dy = gaze.y - centre
        val direction = when {
            abs(dy) < settings.lookStrength -> null
            dy > 0 -> GazeStep.Next
            else -> GazeStep.Previous
        }

        if (direction == null) {
            if (abs(dy) < settings.lookStrength / 2) restY = centre + dy * REST_FOLLOW
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
        log.write("gaze", "look ${if (dy > 0) "down" else "up"} ${format(dy)} held $heldMs ms -> ${direction.name.lowercase()}")
        return direction
    }

    private fun format(value: Float) = String.format(Locale.US, "%.2f", value)

    private companion object {
        /** Share of the way the resting gaze moves toward the current one per frame at rest. */
        const val REST_FOLLOW = 0.05f
        const val REST_RESET_MS = 3_000L
    }
}
