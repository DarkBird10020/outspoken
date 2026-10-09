package com.outspoken.blink

import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Eye-open values below [closedBelow] count as shut, above [openAbove] as open; in between keeps
 * the current state so noise near one line does not flicker. Calibration sets these per person.
 *
 * [shapeClosedBelow] and [shapeOpenAbove], when set, add a second check on the eyelid gap
 * (`eyeShape`): the eyes only count as shut when both readings say so, and they count as open
 * again when either does. On the phone, looking down at the screen pushed the eye-open value as
 * low as a real close (0.55 to 0.65 against 0.48) while the lid gap only halved (0.15 against
 * 0.04 to 0.09 when closed), so the gap tells the two apart.
 */
data class BlinkSettings(
    val closedBelow: Float = 0.3f,
    val openAbove: Float = 0.5f,
    val minBlinkMs: Long = 300,
    val maxBlinkMs: Long = 900,
    // Faces turned further left or right than this count as looking away.
    val maxHeadTurnDeg: Float = 18f,
    // Looking down at a phone on a table tilts the head, so tilt gets more room.
    val maxHeadTiltDeg: Float = 25f,
    // Face trackers drop single frames, often while the eyes are shut. Only a longer gap counts
    // as the face being gone.
    val faceLostAfterMs: Long = 400,
    val shapeClosedBelow: Float? = null,
    val shapeOpenAbove: Float? = null,
)

sealed interface BlinkEvent {
    /** An intentional blink. [startMs] is when the eyes shut. */
    data class Blink(val startMs: Long, val durationMs: Long) : BlinkEvent
    data object FaceLost : BlinkEvent
    data object FaceFound : BlinkEvent
}

/**
 * Turns eye samples into blink events. Both eyes must shut together, so a wink does not count.
 * Normal fast blinks and long closures fall outside the blink window and are ignored.
 */
class BlinkDetector(
    var settings: BlinkSettings = BlinkSettings(),
    private val log: EventLog = EventLog.None,
) {

    var tracking = false
        private set

    private var closedSinceMs: Long? = null
    private var missingSinceMs: Long? = null

    /** When the eyes shut, while they are still shut; null while they are open. */
    val shutSinceMs: Long? get() = closedSinceMs

    fun onSample(sample: EyeSample): BlinkEvent? {
        val left = sample.leftOpen
        val right = sample.rightOpen
        if (!sample.faceFound || left == null || right == null || !facingCamera(sample)) {
            val missingSince = missingSinceMs ?: sample.timeMs.also { missingSinceMs = it }
            if (!tracking || sample.timeMs - missingSince < settings.faceLostAfterMs) return null
            if (closedSinceMs != null) log.write("blink", "closure cancelled, face lost")
            closedSinceMs = null
            tracking = false
            log.write("blink", "face lost: ${lostReason(sample)}")
            return BlinkEvent.FaceLost
        }

        missingSinceMs = null
        val wasTracking = tracking
        tracking = true
        if (!wasTracking) log.write("blink", "face found")
        val closedSince = closedSinceMs
        if (closedSince == null) {
            if (left < settings.closedBelow && right < settings.closedBelow && lidsShut(sample)) {
                closedSinceMs = sample.timeMs
                val gaze = sample.gaze?.let { ", gaze up/down ${open(it.y)}" } ?: ""
                val shape = sample.dots?.let { d -> ", lid gap ${d.leftShape?.let { open(it) }} / ${d.rightShape?.let { open(it) }}" } ?: ""
                log.write("blink", "eyes shut (left ${open(left)}, right ${open(right)}$shape$gaze)")
            }
        } else if (maxOf(left, right) > settings.openAbove || lidsOpen(sample)) {
            closedSinceMs = null
            val duration = sample.timeMs - closedSince
            if (duration in settings.minBlinkMs..settings.maxBlinkMs) {
                log.write("blink", "blink $duration ms")
                return BlinkEvent.Blink(closedSince, duration)
            }
            val why = if (duration < settings.minBlinkMs) {
                "shorter than ${settings.minBlinkMs} ms"
            } else {
                "longer than ${settings.maxBlinkMs} ms"
            }
            log.write("blink", "ignored $duration ms, $why")
        }
        return if (wasTracking) null else BlinkEvent.FaceFound
    }

    /** Whether this frame reads as eyes shut: both eye-open values and both lid gaps below the lines. */
    fun eyesShut(sample: EyeSample): Boolean {
        val left = sample.leftOpen ?: return false
        val right = sample.rightOpen ?: return false
        return left < settings.closedBelow && right < settings.closedBelow && lidsShut(sample)
    }

    /** Both lid gaps below the shut line, or true when there is no gap check or no gap reading. */
    private fun lidsShut(sample: EyeSample): Boolean {
        val line = settings.shapeClosedBelow ?: return true
        val dots = sample.dots ?: return true
        val left = dots.leftShape ?: return true
        val right = dots.rightShape ?: return true
        return left < line && right < line
    }

    /** Either lid gap above the open line. */
    private fun lidsOpen(sample: EyeSample): Boolean {
        val line = settings.shapeOpenAbove ?: return false
        val dots = sample.dots ?: return false
        return maxOf(dots.leftShape ?: 0f, dots.rightShape ?: 0f) > line
    }

    private fun facingCamera(sample: EyeSample) =
        abs(sample.yawDeg) <= settings.maxHeadTurnDeg && abs(sample.pitchDeg) <= settings.maxHeadTiltDeg

    private fun lostReason(sample: EyeSample) = when {
        !sample.faceFound -> "no face"
        sample.leftOpen == null || sample.rightOpen == null -> "eyes not read"
        else -> "head turned (yaw ${sample.yawDeg.roundToInt()}, pitch ${sample.pitchDeg.roundToInt()})"
    }

    private fun open(value: Float) = String.format(Locale.US, "%.2f", value)
}
