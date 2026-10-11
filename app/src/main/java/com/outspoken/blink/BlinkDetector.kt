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
    /**
     * The right eye's own lid gap lines, when calibration measured each eye; [shapeClosedBelow]
     * and [shapeOpenAbove] are then the left eye's. With glasses the owner's eyes differed (17:18
     * calibration): closed left 0.07 to 0.11, right 0.05 to 0.08; looking down left 0.18 to 0.22,
     * right 0.11 to 0.17. One line for both was too low for the left eye and too high for the right.
     */
    val rightShapeClosedBelow: Float? = null,
    val rightShapeOpenAbove: Float? = null,
    /**
     * Each eye's lid gap at rest when the lines were set (calibration), so the lines can follow
     * how wide the eyes read open now; null for lines from before this was saved.
     */
    val gapRest: Float? = null,
    val rightGapRest: Float? = null,
    /**
     * Choose as soon as the eyes have been shut for [minBlinkMs], without waiting for them to
     * open again. Waiting for the reopen added the whole rest of the close to every choice, which
     * felt slow on the phone. [maxBlinkMs] then no longer applies.
     */
    val chooseWhileShut: Boolean = false,
    /**
     * Weight of the newest frame in a running average of the eye-open values (1 = no smoothing).
     * Evens out single-frame jitter; at 0.65 and 25 fps it adds about 15 ms.
     */
    val smoothing: Float = 1f,
    /**
     * Two quick closes in a row also choose, however short each is. With glasses on the owner's
     * closes came through at 109 to 168 ms and were ignored; people blink again when a close does
     * not take, and natural double blinks are rare.
     */
    val doubleBlink: Boolean = false,
) {
    /** One eye's lid gap shut line: the right eye's own when calibration set it. */
    fun gapShutLine(rightEye: Boolean): Float? = if (rightEye) rightShapeClosedBelow ?: shapeClosedBelow else shapeClosedBelow

    /** One eye's lid gap open line: the right eye's own when calibration set it. */
    fun gapOpenLine(rightEye: Boolean): Float? = if (rightEye) rightShapeOpenAbove ?: shapeOpenAbove else shapeOpenAbove

    /** One eye's lid gap at rest when the lines were set. */
    fun gapRestAt(rightEye: Boolean): Float? = if (rightEye) rightGapRest ?: gapRest else gapRest
}

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
    settings: BlinkSettings = BlinkSettings(),
    private val log: EventLog = EventLog.None,
) {

    var settings = settings
        set(value) {
            field = value
            missedInARow = 0
            rescale(force = true)
        }

    /**
     * How wide each eye reads open now, against [BlinkSettings.gapRest]. The lid gap lines are
     * scaled by it, so they follow the eyes when the phone sits differently from the calibration.
     * On the phone the lines from a 21:07 calibration (open 0.24 / 0.21, shut lines 0.10 / 0.12)
     * were kept the next morning when the calibration was skipped (05:46:53); the eyes then read
     * open at 0.31 / 0.28, and closes came down only to 0.10 to 0.16: 15 were not counted between
     * 05:47:37 and 06:01:40, and the person closed harder and longer to get a card.
     */
    private val leftOpenGap = OpenGap()
    private val rightOpenGap = OpenGap()
    private var leftScale = 1f
    private var rightScale = 1f
    private var loggedLeftScale = 1f
    private var loggedRightScale = 1f
    private var framesSinceRescale = 0

    /**
     * Closes in a row that the lid gap kept from counting ([halfShutEnded]); a counted close or
     * new lines reset it. On the phone with glasses, seven closes of 0.9 to 2 s in a row were
     * missed this way (17:14:27 to 17:14:57) under lines set without glasses.
     */
    var missedInARow = 0
        private set

    var tracking = false
        private set

    private var closedSinceMs: Long? = null
    private var missingSinceMs: Long? = null

    /** This close already chose a card, so its reopen must not choose again. */
    private var chosen = false
    private var lastQuickCloseEndMs: Long? = null

    /** Last frame of this close that was read as still shut, and whether the face went missing after it. */
    private var lastShutSeenMs = 0L
    private var faceGoneSinceShutSeen = false
    private var smoothLeft: Float? = null
    private var smoothRight: Float? = null

    // Eyes the eye-open values read as shut while the lid gaps stayed above their lines: when that
    // began, the narrowest gap of each eye and the gaze, for one log line when it ends. Glasses
    // keep the gap wider when closed (0.10 to 0.15 at 17:14), and looking down does too, so the
    // line lets the logs tell the two apart.
    private var halfShutSinceMs: Long? = null
    private var halfShutLeftGap: Float? = null
    private var halfShutRightGap: Float? = null
    private var halfShutGaze: Float? = null

    /** When the eyes shut, while they are still shut; null while they are open. */
    val shutSinceMs: Long? get() = closedSinceMs

    /**
     * Forgets a close or half shut in progress, for when this detector stops getting frames (its
     * page is left). Otherwise the close ran on until the page came back: "half shut 36211 ms"
     * on the eye check page (06:16:20) and "ignored 20405 ms" on the main page (06:16:14), each
     * spanning the time on other pages.
     */
    fun reset() {
        closedSinceMs = null
        halfShutSinceMs = null
        chosen = false
        smoothLeft = null
        smoothRight = null
        missingSinceMs = null
    }

    /** How long the last close that ended with the eyes seen open lasted, time with no face left out. */
    var lastCloseMs = 0L
        private set

    fun onSample(sample: EyeSample): BlinkEvent? {
        val rawLeft = sample.leftOpen
        val rawRight = sample.rightOpen
        val facing = facingCamera(sample)
        // Eyelid closure deforms the face mesh landmarks, causing transient yaw jumps.
        // If a closure is already in progress, tolerate this deformation while the face is still found.
        val inClosure = closedSinceMs != null
        val acceptableOrientation = facing || (inClosure && sample.faceFound && abs(sample.yawDeg) <= 60f)

        if (!sample.faceFound || rawLeft == null || rawRight == null || !acceptableOrientation) {
            val missingSince = missingSinceMs ?: sample.timeMs.also { missingSinceMs = it }
            if (closedSinceMs != null) faceGoneSinceShutSeen = true
            if (!tracking || sample.timeMs - missingSince < settings.faceLostAfterMs) return null
            if (closedSinceMs != null) log.write("blink", "closure cancelled, face lost")
            closedSinceMs = null
            halfShutSinceMs = null
            chosen = false
            smoothLeft = null
            smoothRight = null
            tracking = false
            log.write("blink", "face lost: ${lostReason(sample)}")
            return BlinkEvent.FaceLost
        }

        missingSinceMs = null
        val left = smooth(smoothLeft, rawLeft).also { smoothLeft = it }
        val right = smooth(smoothRight, rawRight).also { smoothRight = it }
        val wasTracking = tracking
        tracking = true
        if (!wasTracking) log.write("blink", "face found")
        val closedSince = closedSinceMs
        val avgOpen = (left + right) / 2f
        if (closedSince == null && halfShutSinceMs == null && minOf(left, right) > settings.openAbove) noteOpenGaps(sample)
        if (closedSince == null) {
            val openSaysShut = (left < settings.closedBelow && right < settings.closedBelow) ||
                (avgOpen < settings.closedBelow && minOf(left, right) < settings.closedBelow + 0.08f)
            val bothShut = openSaysShut && lidsShut(sample)
            when {
                bothShut -> halfShutSinceMs = null
                openSaysShut && settings.shapeClosedBelow != null -> halfShut(sample)
                else -> halfShutEnded(sample.timeMs)
            }
            if (bothShut) {
                closedSinceMs = sample.timeMs
                lastShutSeenMs = sample.timeMs
                faceGoneSinceShutSeen = false
                val gaze = sample.gaze?.let { ", gaze up/down ${open(it.y)}" } ?: ""
                val shape = sample.dots?.let { d -> ", lid gap ${d.leftShape?.let { open(it) }} / ${d.rightShape?.let { open(it) }}" } ?: ""
                log.write("blink", "eyes shut (left ${open(left)}, right ${open(right)}$shape$gaze)")
            }
        } else if (avgOpen > settings.openAbove || maxOf(left, right) > settings.openAbove || lidsOpen(sample)) {
            closedSinceMs = null
            // Time with no face is not time seen shut. On the phone (03:24:07 run) eyes seen shut
            // for under 200 ms, then no face for about 200 ms, came back open and picked
            // "I need water" as a 397 ms blink.
            val seenUntil = if (faceGoneSinceShutSeen) lastShutSeenMs else sample.timeMs
            val duration = seenUntil - closedSince
            lastCloseMs = duration
            if (chosen) {
                chosen = false
                log.write("blink", "eyes open after $duration ms")
                return if (wasTracking) null else BlinkEvent.FaceFound
            }
            if (duration in settings.minBlinkMs..settings.maxBlinkMs) {
                missedInARow = 0
                log.write("blink", "blink $duration ms")
                return BlinkEvent.Blink(closedSince, duration)
            }
            if (settings.doubleBlink && duration in DOUBLE_MIN_MS until settings.minBlinkMs) {
                val previous = lastQuickCloseEndMs
                lastQuickCloseEndMs = sample.timeMs
                if (previous != null && closedSince - previous <= DOUBLE_GAP_MS) {
                    lastQuickCloseEndMs = null
                    log.write("blink", "double blink, second close $duration ms")
                    return BlinkEvent.Blink(closedSince, duration)
                }
            }
            val why = if (duration < settings.minBlinkMs) {
                "shorter than ${settings.minBlinkMs} ms"
            } else {
                "longer than ${settings.maxBlinkMs} ms"
            }
            val gone = if (faceGoneSinceShutSeen) " seen shut, then no face" else ""
            log.write("blink", "ignored $duration ms$gone, $why")
        } else {
            lastShutSeenMs = sample.timeMs
            faceGoneSinceShutSeen = false
            if (settings.chooseWhileShut && !chosen && sample.timeMs - closedSince >= settings.minBlinkMs) {
                chosen = true
                missedInARow = 0
                val duration = sample.timeMs - closedSince
                log.write("blink", "blink $duration ms, chosen with the eyes still shut")
                return BlinkEvent.Blink(closedSince, duration)
            }
        }
        return if (wasTracking) null else BlinkEvent.FaceFound
    }

    private fun halfShut(sample: EyeSample) {
        if (halfShutSinceMs == null) {
            halfShutSinceMs = sample.timeMs
            halfShutLeftGap = null
            halfShutRightGap = null
            halfShutGaze = sample.gaze?.y
        }
        val dots = sample.dots ?: return
        dots.leftShape?.let { gap -> halfShutLeftGap = minOf(halfShutLeftGap ?: gap, gap) }
        dots.rightShape?.let { gap -> halfShutRightGap = minOf(halfShutRightGap ?: gap, gap) }
    }

    private fun halfShutEnded(timeMs: Long) {
        val since = halfShutSinceMs ?: return
        halfShutSinceMs = null
        val duration = timeMs - since
        if (duration < settings.minBlinkMs) return
        fun gap(value: Float?) = value?.let { open(it) } ?: "not read"
        fun line(rightEye: Boolean) = shutLine(rightEye)?.let { open(it) } ?: "off"
        val gaze = halfShutGaze?.let { ", gaze up/down ${open(it)}" } ?: ""
        val gaps = "left ${gap(halfShutLeftGap)} / right ${gap(halfShutRightGap)} (shut lines ${line(false)} / ${line(true)}$gaze)"
        // Lids that stayed at the look-down height (above the open lines, which sit three quarters
        // of the way from closed to looking down) were a look down, not a close the lines missed:
        // 17:51:58 and 17:52:08 read 0.21 / 0.16 against a calibrated look down of 0.18 / 0.16, and
        // asked for a calibration nobody needed.
        val nearlyShut = pastLines(halfShutLeftGap, halfShutRightGap) { openLine(it) }?.let { it < 0f } ?: true
        if (!nearlyShut) {
            log.write("blink", "half shut $duration ms with the lids at the look-down height, a look down: $gaps")
            return
        }
        log.write("blink", "half shut $duration ms, not counted: eye-open read shut but the lid gaps came down only to $gaps")
        missedInARow++
        if (missedInARow == MISSED_BEFORE_ASKING) {
            log.write("blink", "$missedInARow closes in a row not counted; asking for a calibration (with glasses on, if worn)")
        }
    }

    private fun smooth(previous: Float?, value: Float) =
        previous?.let { it + settings.smoothing * (value - it) } ?: value

    /** Whether this frame reads as eyes shut: both eye-open values and both lid gaps below the lines. */
    fun eyesShut(sample: EyeSample): Boolean {
        val left = sample.leftOpen ?: return false
        val right = sample.rightOpen ?: return false
        return left < settings.closedBelow && right < settings.closedBelow && lidsShut(sample)
    }

    /**
     * Whether at least one eye reads shut: its eye-open value below the line and, when the lid gap
     * check is on, its lid gap below the gap line. Looking down lowers the eye-open value but not
     * the lid gap, so it does not count; a wink does.
     */
    fun eitherEyeShut(sample: EyeSample): Boolean {
        fun shut(open: Float?, gap: Float?, rightEye: Boolean): Boolean {
            val gapLine = shutLine(rightEye)
            return open != null && open < settings.closedBelow && (gapLine == null || gap == null || gap < gapLine)
        }
        return shut(sample.leftOpen, sample.dots?.leftShape, false) || shut(sample.rightOpen, sample.dots?.rightShape, true)
    }

    /**
     * How far both lid gaps are past their lines, together: each eye's distance from [line] in
     * units of its own shut-to-open span, added. Below 0 the eyes are on the whole below the
     * lines. Taken together so that one eye kept wider by a glasses lens (left 0.12 to 0.14 while
     * the right was at 0.06 to 0.08, 17:14) does not stop a close both eyes made. Null when the
     * gap check is off or a gap is not read.
     */
    private fun pastLines(sample: EyeSample, line: (rightEye: Boolean) -> Float?): Float? {
        val dots = sample.dots ?: return null
        return pastLines(dots.leftShape, dots.rightShape, line)
    }

    private fun pastLines(leftGap: Float?, rightGap: Float?, line: (rightEye: Boolean) -> Float?): Float? {
        fun eye(gap: Float?, rightEye: Boolean): Float? {
            val at = line(rightEye) ?: return null
            val shut = shutLine(rightEye) ?: return null
            val span = openLine(rightEye)?.let { it - shut } ?: 0f
            return ((gap ?: return null) - at) / maxOf(span, MIN_GAP_SPAN)
        }
        return (eye(leftGap, false) ?: return null) + (eye(rightGap, true) ?: return null)
    }

    /** Both lid gaps, together, below their shut lines; true when there is no gap check or reading. */
    private fun lidsShut(sample: EyeSample): Boolean = pastLines(sample) { shutLine(it) }?.let { it < 0f } ?: true

    /** Both lid gaps, together, above their open lines. */
    private fun lidsOpen(sample: EyeSample): Boolean = pastLines(sample) { openLine(it) }?.let { it > 0f } ?: false

    /** One eye's lid gap shut line, scaled to how wide the eyes read open now. */
    fun shutLine(rightEye: Boolean): Float? = settings.gapShutLine(rightEye)?.times(scale(rightEye))

    /** One eye's lid gap open line, scaled to how wide the eyes read open now. */
    fun openLine(rightEye: Boolean): Float? = settings.gapOpenLine(rightEye)?.times(scale(rightEye))

    private fun scale(rightEye: Boolean) = if (rightEye) rightScale else leftScale

    /** Keeps the lid gaps of a frame with both eyes clearly open, and rescales about once a second. */
    private fun noteOpenGaps(sample: EyeSample) {
        val dots = sample.dots ?: return
        dots.leftShape?.let(leftOpenGap::add)
        dots.rightShape?.let(rightOpenGap::add)
        if (++framesSinceRescale >= RESCALE_EVERY_FRAMES) rescale(force = false)
    }

    private fun rescale(force: Boolean) {
        framesSinceRescale = 0
        fun scaleFor(gaps: OpenGap, rightEye: Boolean): Float {
            val calibrated = settings.gapRestAt(rightEye) ?: return 1f
            val now = gaps.median() ?: return 1f
            return (now / calibrated).coerceIn(MIN_GAP_SCALE, MAX_GAP_SCALE)
        }
        leftScale = scaleFor(leftOpenGap, false)
        rightScale = scaleFor(rightOpenGap, true)
        val moved = abs(leftScale - loggedLeftScale) >= LOG_SCALE_STEP || abs(rightScale - loggedRightScale) >= LOG_SCALE_STEP
        if (!moved && !force) return
        if (!moved && leftScale == 1f && rightScale == 1f) return
        loggedLeftScale = leftScale
        loggedRightScale = rightScale
        val rests = "${settings.gapRestAt(false)?.let { open(it) }} / ${settings.gapRestAt(true)?.let { open(it) }}"
        val now = "${leftOpenGap.median()?.let { open(it) }} / ${rightOpenGap.median()?.let { open(it) }}"
        log.write(
            "blink",
            "lid gap lines x${open(leftScale)} / x${open(rightScale)}: eyes read open at $now, $rests at calibration; " +
                "shut lines ${shutLine(false)?.let { open(it) }} / ${shutLine(true)?.let { open(it) }}",
        )
    }

    private fun facingCamera(sample: EyeSample) =
        abs(sample.yawDeg) <= settings.maxHeadTurnDeg && abs(sample.pitchDeg) <= settings.maxHeadTiltDeg

    private fun lostReason(sample: EyeSample) = when {
        !sample.faceFound -> "no face"
        sample.leftOpen == null || sample.rightOpen == null -> "eyes not read"
        else -> "head turned (yaw ${sample.yawDeg.roundToInt()}, pitch ${sample.pitchDeg.roundToInt()})"
    }

    private fun open(value: Float) = String.format(Locale.US, "%.2f", value)

    companion object {
        /** Missed closes in a row before the main page asks for a new calibration. */
        const val MISSED_BEFORE_ASKING = 2

        /** Smallest shut-to-open span used to scale a gap, so lines set almost together still compare. */
        private const val MIN_GAP_SPAN = 0.01f

        private const val RESCALE_EVERY_FRAMES = 25

        /**
         * How far the lines may follow the eyes. The 05:46 morning run read 1.3 times the
         * calibration's open gaps; a scale far outside this is more likely a bad reading.
         */
        private const val MIN_GAP_SCALE = 0.7f
        private const val MAX_GAP_SCALE = 1.6f

        /** A change in scale this big is logged. */
        private const val LOG_SCALE_STEP = 0.1f

        /** Shorter closes are noise or the start of a normal blink. */
        private const val DOUBLE_MIN_MS = 60L

        /** Most the gap between the two closes of a double blink may be. */
        private const val DOUBLE_GAP_MS = 800L
    }
}
