package com.outspoken.scan

import com.outspoken.eye.Dot
import com.outspoken.log.EventLog
import java.util.Locale
import kotlin.math.abs

data class GazeSettings(
    /** How far the eyes must move up from where they rest to count as a look (0..1). */
    val lookStrength: Float = 0.45f,
    /** How long a look must last before the highlight moves. */
    val lookHoldMs: Long = 250,
)

enum class GazeStep { Next, Previous }

/**
 * Turns a look up, above the phone, into one step to the next card. Only up counts: on the phone,
 * looking down drops the upper lids and the face tracker reads it as the eyes closing (seen in the
 * logs: gaze down 0.81 to 0.87 arrived as "eyes shut"), so down looks would both miss and fake
 * blinks. Sideways looks are ignored too. One look is one step; the eyes come back to rest before
 * the next. Looks are measured from the resting gaze, which slowly follows posture.
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
        val direction = if (-dy >= settings.lookStrength && gaze.y < LOOK_UP_MAX_Y) GazeStep.Next else null

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
        log.write("gaze", "look up ${format(-dy)} held $heldMs ms -> next")
        return direction
    }

    private fun format(value: Float) = String.format(Locale.US, "%.2f", value)

    private companion object {
        /** Share of the way the resting gaze moves toward the current one per frame at rest. */
        const val REST_FOLLOW = 0.05f
        const val REST_RESET_MS = 3_000L
        /** Eyes must look toward or above the top bezel, not just glance within the screen cards. */
        const val LOOK_UP_MAX_Y = 0.20f
    }
}
