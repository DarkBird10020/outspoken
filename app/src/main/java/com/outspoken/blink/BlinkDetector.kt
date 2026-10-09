package com.outspoken.blink

import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shut and open lines are fractions of the person's own open-eye level, so droopy lids or a
 * phone below eye level do not read as shut. The gap between the two lines keeps noise near
 * one line from flickering.
 */
data class BlinkSettings(
    val closedRatio: Float = 0.4f,
    val openRatio: Float = 0.65f,
    val minBlinkMs: Long = 500,
    val maxBlinkMs: Long = 1500,
    // ML Kit only gives eye-open values for faces turned left or right at most 18 degrees.
    val maxYawDeg: Float = 18f,
    // Looking down at a phone on a table tilts the head, and ML Kit sets no limit for it.
    val maxPitchDeg: Float = 35f,
    val faceLostAfterMs: Long = 500,
    val openFramesToEnd: Int = 2,
    val openLevelFollowMs: Long = 4_000,
)

sealed interface BlinkEvent {
    /** An intentional blink. [startMs] is when the eyes shut. */
    data class Blink(val startMs: Long, val durationMs: Long) : BlinkEvent

    /** Eyes shut and opened again, but too short or too long to be a choice. */
    data class Rejected(val startMs: Long, val durationMs: Long) : BlinkEvent
    data object FaceLost : BlinkEvent
    data object FaceFound : BlinkEvent
}

data class Closure(val durationMs: Long, val accepted: Boolean)

/**
 * Turns eye samples into blink events. Both eyes must shut together, so a wink does not count.
 * A closure ends only after [BlinkSettings.openFramesToEnd] open frames, so one noisy frame
 * cannot split a blink, and a face missing for less than [BlinkSettings.faceLostAfterMs] is
 * ignored.
 */
class BlinkDetector(
    var settings: BlinkSettings = BlinkSettings(),
    private val log: EventLog = EventLog.None,
) {

    /** The person's open-eye level, 0 to 1. Learned while the eyes are open; calibration sets it. */
    var openLevel = DEFAULT_OPEN_LEVEL

    val closedBelow get() = (openLevel * settings.closedRatio).coerceIn(0.05f, 0.45f)
    val openAbove get() = (openLevel * settings.openRatio).coerceIn(closedBelow + 0.05f, 0.85f)

    var tracking = false
        private set

    /** Average of both eyes in the last usable frame. */
    var eyeLevel = 0f
        private set

    val eyesShut get() = closedSinceMs != null

    private val closures = ArrayDeque<Closure>()
    val recentClosures: List<Closure> get() = closures.toList()

    private var lastUsableMs: Long? = null
    private var lastLearnMs: Long? = null
    private var closedSinceMs: Long? = null
    private var openSinceMs = 0L
    private var openStreak = 0

    fun onSample(sample: EyeSample): BlinkEvent? {
        val left = sample.leftOpen
        val right = sample.rightOpen
        if (!sample.faceFound || left == null || right == null || !facingCamera(sample)) {
            val lastSeen = lastUsableMs ?: return null
            if (!tracking || sample.timeMs - lastSeen < settings.faceLostAfterMs) return null
            if (closedSinceMs != null) log.write("blink", "closure cancelled, face lost")
            log.write("blink", "face lost: ${lostReason(sample)}")
            tracking = false
            closedSinceMs = null
            openStreak = 0
            lastLearnMs = null
            return BlinkEvent.FaceLost
        }

        lastUsableMs = sample.timeMs
        eyeLevel = (left + right) / 2
        val wasTracking = tracking
        tracking = true
        if (!wasTracking) log.write("blink", "face found")
        val event = eyes(sample.timeMs, left, right)
        return event ?: if (wasTracking) null else BlinkEvent.FaceFound
    }

    private fun eyes(timeMs: Long, left: Float, right: Float): BlinkEvent? {
        val closedSince = closedSinceMs
        if (closedSince == null) {
            if (left < closedBelow && right < closedBelow) {
                log.write("blink", "eyes shut (left ${open(left)}, right ${open(right)}, line ${open(closedBelow)})")
                closedSinceMs = timeMs
                openStreak = 0
                lastLearnMs = null
            } else {
                learnOpenLevel(timeMs)
            }
            return null
        }

        if (maxOf(left, right) <= openAbove) {
            openStreak = 0
            // Shut far longer than any blink: these lids are this person's open level.
            if (timeMs - closedSince > STUCK_MS) {
                closedSinceMs = null
                openLevel = eyeLevel / settings.openRatio
                log.write("blink", "shut for over ${STUCK_MS / 1000} s, taking ${open(eyeLevel)} as open eyes")
            }
            return null
        }

        if (openStreak == 0) openSinceMs = timeMs
        openStreak++
        if (openStreak < settings.openFramesToEnd) return null

        closedSinceMs = null
        openStreak = 0
        val duration = openSinceMs - closedSince
        val accepted = duration in settings.minBlinkMs..settings.maxBlinkMs
        remember(Closure(duration, accepted))
        if (accepted) {
            log.write("blink", "blink $duration ms")
            return BlinkEvent.Blink(closedSince, duration)
        }
        val why = if (duration < settings.minBlinkMs) "shorter than ${settings.minBlinkMs} ms" else "longer than ${settings.maxBlinkMs} ms"
        log.write("blink", "ignored $duration ms, $why")
        return BlinkEvent.Rejected(closedSince, duration)
    }

    private fun learnOpenLevel(timeMs: Long) {
        val last = lastLearnMs
        lastLearnMs = timeMs
        if (last == null || timeMs <= last) return
        val step = (timeMs - last).toFloat()
        openLevel += (eyeLevel - openLevel) * step / (settings.openLevelFollowMs + step)
    }

    private fun remember(closure: Closure) {
        closures.addLast(closure)
        while (closures.size > RECENT_CLOSURES) closures.removeFirst()
    }

    private fun facingCamera(sample: EyeSample) =
        abs(sample.yawDeg) <= settings.maxYawDeg && abs(sample.pitchDeg) <= settings.maxPitchDeg

    private fun lostReason(sample: EyeSample) = when {
        !sample.faceFound -> "no face"
        sample.leftOpen == null || sample.rightOpen == null -> "eyes not read"
        else -> "head turned (yaw ${sample.yawDeg.roundToInt()}, pitch ${sample.pitchDeg.roundToInt()})"
    }

    private fun open(value: Float) = String.format(Locale.US, "%.2f", value)

    companion object {
        const val DEFAULT_OPEN_LEVEL = 0.9f
        const val STUCK_MS = 5_000L
        const val RECENT_CLOSURES = 5
    }
}
