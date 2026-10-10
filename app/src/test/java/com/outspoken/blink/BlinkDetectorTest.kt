package com.outspoken.blink

import com.outspoken.eye.EyeSample
import com.outspoken.eye.FaceDots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkDetectorTest {

    private val detector = BlinkDetector()
    private var time = 1_000L

    private fun eyes(left: Float, right: Float = left, yaw: Float = 0f) =
        EyeSample(time, faceFound = true, leftOpen = left, rightOpen = right, yawDeg = yaw)

    /** Feeds one sample per 33 ms frame for [ms] and returns the events seen. */
    private fun hold(ms: Long, sample: () -> EyeSample): List<BlinkEvent> {
        val events = mutableListOf<BlinkEvent>()
        val end = time + ms
        while (time < end) {
            detector.onSample(sample())?.let(events::add)
            time += 33
        }
        return events
    }

    private fun blink(closedMs: Long, left: Float = 0.05f, right: Float = 0.05f): List<BlinkEvent> {
        hold(500) { eyes(0.95f) }
        return hold(closedMs) { eyes(left, right) } + hold(300) { eyes(0.95f) }
    }

    @Test
    fun `first face reports found`() {
        assertEquals(listOf(BlinkEvent.FaceFound), hold(100) { eyes(0.95f) })
        assertTrue(detector.tracking)
    }

    @Test
    fun `half second blink is intentional`() {
        val events = blink(500)
        val blink = events.single() as BlinkEvent.Blink
        assertTrue(blink.durationMs in 495..530)
    }

    @Test
    fun `blink event starts when the eyes shut`() {
        hold(500) { eyes(0.95f) }
        val shutAt = time
        val events = hold(500) { eyes(0.05f) } + hold(100) { eyes(0.95f) }
        assertEquals(shutAt, (events.single() as BlinkEvent.Blink).startMs)
    }

    @Test
    fun `the last close length is kept, chosen or not`() {
        blink(500)
        assertTrue(detector.lastCloseMs in 495..530)
        blink(1_700)
        assertTrue(detector.lastCloseMs in 1_695..1_730)
    }

    @Test
    fun `normal fast blink is ignored`() {
        assertEquals(emptyList<BlinkEvent>(), blink(150))
    }

    @Test
    fun `long closure is ignored`() {
        assertEquals(emptyList<BlinkEvent>(), blink(1_500))
    }

    @Test
    fun `wink does not count`() {
        assertEquals(emptyList<BlinkEvent>(), blink(500, left = 0.05f, right = 0.95f))
    }

    @Test
    fun `values between the lines keep the eyes shut`() {
        hold(500) { eyes(0.95f) }
        val events = hold(200) { eyes(0.05f) } + hold(300) { eyes(0.4f) } + hold(100) { eyes(0.95f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `losing the face cancels a blink`() {
        hold(500) { eyes(0.95f) }
        val events = hold(200) { eyes(0.05f) } +
            hold(500) { EyeSample(time, faceFound = false) } +
            hold(200) { eyes(0.05f) } +
            hold(100) { eyes(0.95f) }
        assertEquals(listOf(BlinkEvent.FaceLost, BlinkEvent.FaceFound), events)
    }

    @Test
    fun `a short face dropout keeps the blink going`() {
        hold(500) { eyes(0.95f) }
        val events = hold(200) { eyes(0.05f) } +
            hold(100) { EyeSample(time, faceFound = false) } +
            hold(200) { eyes(0.05f) } +
            hold(100) { eyes(0.95f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `time with no face does not count as shut`() {
        // Phone run 03:24:07: seen shut under 200 ms, no face for about 200 ms, back open.
        hold(500) { eyes(0.95f) }
        val events = hold(150) { eyes(0.05f) } +
            hold(300) { EyeSample(time, faceFound = false) } +
            hold(100) { eyes(0.95f) }
        assertEquals(emptyList<BlinkEvent>(), events)
    }

    @Test
    fun `no face while choosing while shut does not choose`() {
        detector.settings = BlinkSettings(minBlinkMs = 400, maxBlinkMs = 1_500, chooseWhileShut = true)
        hold(500) { eyes(0.95f) }
        val events = hold(180) { eyes(0.05f) } +
            hold(350) { EyeSample(time, faceFound = false) } +
            hold(100) { eyes(0.95f) }
        assertEquals(emptyList<BlinkEvent>(), events)
    }

    @Test
    fun `a short face dropout is not face lost`() {
        hold(100) { eyes(0.95f) }
        assertEquals(emptyList<BlinkEvent>(), hold(300) { EyeSample(time, faceFound = false) })
        assertTrue(detector.tracking)
    }

    @Test
    fun `turning away counts as face lost`() {
        hold(100) { eyes(0.95f) }
        assertEquals(listOf(BlinkEvent.FaceLost), hold(500) { eyes(0.95f, yaw = 40f) })
        assertFalse(detector.tracking)
    }

    @Test
    fun `window edges are inclusive`() {
        detector.settings = BlinkSettings(minBlinkMs = 300, maxBlinkMs = 900)
        detector.onSample(EyeSample(0, true, 0.95f, 0.95f))
        detector.onSample(EyeSample(100, true, 0.05f, 0.05f))
        assertTrue(detector.onSample(EyeSample(400, true, 0.95f, 0.95f)) is BlinkEvent.Blink)
        detector.onSample(EyeSample(1_000, true, 0.05f, 0.05f))
        assertTrue(detector.onSample(EyeSample(1_900, true, 0.95f, 0.95f)) is BlinkEvent.Blink)
        detector.onSample(EyeSample(3_000, true, 0.05f, 0.05f))
        assertNull(detector.onSample(EyeSample(3_901, true, 0.95f, 0.95f)))
    }

    private fun withGap(open: Float, gap: Float) =
        EyeSample(time, true, open, open, dots = FaceDots(emptyList(), emptyList(), 0.75f, gap, gap))

    private val gapSettings = BlinkSettings(
        closedBelow = 0.68f,
        openAbove = 0.78f,
        minBlinkMs = 400,
        maxBlinkMs = 1_500,
        shapeClosedBelow = 0.13f,
        shapeOpenAbove = 0.18f,
    )

    @Test
    fun `looking down with the lids half open is not a close`() {
        detector.settings = gapSettings
        hold(500) { withGap(0.9f, 0.30f) }
        // Phone log: looking down read 0.55 to 0.65 open with a lid gap of about 0.15.
        val events = hold(1_000) { withGap(0.6f, 0.15f) } + hold(300) { withGap(0.9f, 0.30f) }
        assertEquals(emptyList<BlinkEvent>(), events)
    }

    @Test
    fun `a close the lid gap blocks is logged with its length and narrowest gap`() {
        val lines = mutableListOf<String>()
        val logged = BlinkDetector(gapSettings) { _, message -> lines += message }
        fun feed(ms: Long, sample: () -> EyeSample) {
            val end = time + ms
            while (time < end) {
                logged.onSample(sample())
                time += 33
            }
        }
        feed(500) { withGap(0.9f, 0.30f) }
        // Glasses: the eyes close but the lid gap stays above the line.
        feed(600) { withGap(0.40f, 0.15f) }
        feed(300) { withGap(0.9f, 0.30f) }
        val line = lines.single { it.startsWith("half shut") }
        assertTrue(line, line.contains("came down only to 0.15 (shut line 0.13"))
        // A quick dip is a normal blink and is not logged.
        feed(150) { withGap(0.40f, 0.15f) }
        feed(300) { withGap(0.9f, 0.30f) }
        assertEquals(1, lines.count { it.startsWith("half shut") })
    }

    @Test
    fun `a real close passes both checks`() {
        detector.settings = gapSettings
        hold(500) { withGap(0.9f, 0.30f) }
        val events = hold(500) { withGap(0.45f, 0.06f) } + hold(300) { withGap(0.9f, 0.30f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `opening the lids ends a close while looking down`() {
        detector.settings = gapSettings
        hold(500) { withGap(0.9f, 0.30f) }
        val events = hold(500) { withGap(0.45f, 0.06f) } + hold(300) { withGap(0.6f, 0.22f) }
        val blink = events.single() as BlinkEvent.Blink
        assertTrue(blink.durationMs in 495..530)
    }

    @Test
    fun `choosing while shut fires once, as soon as the close is long enough`() {
        detector.settings = BlinkSettings(minBlinkMs = 300, chooseWhileShut = true)
        hold(500) { eyes(0.95f) }
        val shutAt = time
        val blink = hold(400) { eyes(0.05f) }.single() as BlinkEvent.Blink
        assertEquals(shutAt, blink.startMs)
        assertTrue(blink.durationMs in 300..340)
        assertEquals(emptyList<BlinkEvent>(), hold(300) { eyes(0.95f) })
    }

    @Test
    fun `a normal blink still does nothing when choosing while shut`() {
        detector.settings = BlinkSettings(minBlinkMs = 300, chooseWhileShut = true)
        assertEquals(emptyList<BlinkEvent>(), blink(150))
    }

    @Test
    fun `smoothing ignores a single jittery frame`() {
        detector.settings = BlinkSettings(smoothing = 0.65f)
        hold(500) { eyes(0.95f) }
        val events = hold(33) { eyes(0.1f) } + hold(500) { eyes(0.95f) }
        assertEquals(emptyList<BlinkEvent>(), events)
        hold(500) { eyes(0.95f) }
        // A real half-second close still counts.
        assertTrue((hold(500) { eyes(0.05f) } + hold(300) { eyes(0.95f) }).single() is BlinkEvent.Blink)
    }


    @Test
    fun `one eye shut counts for either eye shut, looking down with open lids does not`() {
        detector.settings = gapSettings
        assertTrue(detector.eitherEyeShut(EyeSample(0, true, 0.4f, 0.95f, dots = FaceDots(emptyList(), emptyList(), 0.75f, 0.05f, 0.30f))))
        assertFalse(detector.eitherEyeShut(withGap(0.6f, 0.15f)))
    }

    @Test
    fun `two quick closes in a row choose`() {
        detector.settings = BlinkSettings(minBlinkMs = 200, doubleBlink = true)
        hold(500) { eyes(0.95f) }
        val events = hold(130) { eyes(0.05f) } + hold(250) { eyes(0.95f) } +
            hold(130) { eyes(0.05f) } + hold(300) { eyes(0.95f) }
        assertTrue(events.single() is BlinkEvent.Blink)
    }

    @Test
    fun `one quick close alone does not choose`() {
        detector.settings = BlinkSettings(minBlinkMs = 200, doubleBlink = true)
        assertEquals(emptyList<BlinkEvent>(), blink(130))
    }

    @Test
    fun `two quick closes far apart do not choose`() {
        detector.settings = BlinkSettings(minBlinkMs = 200, doubleBlink = true)
        hold(500) { eyes(0.95f) }
        val events = hold(130) { eyes(0.05f) } + hold(2_000) { eyes(0.95f) } +
            hold(130) { eyes(0.05f) } + hold(300) { eyes(0.95f) }
        assertEquals(emptyList<BlinkEvent>(), events)
    }
}
