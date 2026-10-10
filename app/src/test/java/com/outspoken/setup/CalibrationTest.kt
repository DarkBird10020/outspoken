package com.outspoken.setup

import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import com.outspoken.eye.FaceDots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {

    private val calibration = Calibration(stepMs = 2_500, settleMs = 700)
    private var time = 0L
    private val spoken = mutableListOf<Calibration.Step>()

    /** Feeds one step of frames: [open] for both eyes, [gazeY] up negative. */
    private fun step(open: Float, gazeY: Float, face: Boolean = true, gap: Float? = null, iris: Float? = null) {
        repeat(50) {
            val dots = gap?.let { FaceDots(emptyList(), emptyList(), 0.75f, it, it) }
            val sample = if (face) EyeSample(time, true, open, open, gaze = Dot(0f, gazeY), dots = dots, irisY = iris) else EyeSample(time, false)
            calibration.onSample(sample)?.let(spoken::add)
            time += 50
        }
    }

    /** A person whose eyes rest at 0.9 open, gaze 0.5, look up to 0.0 and close to 0.4. */
    private fun run(
        upGaze: Float = 0f,
        downGaze: Float = 0.9f,
        closedOpen: Float = 0.4f,
        face: Boolean = true,
        openGap: Float? = null,
        closedGap: Float? = null,
    ) {
        calibration.start(time)
        step(0.9f, 0.5f, face, openGap)
        step(0.9f, upGaze, face, openGap)
        step(0.9f, 0.5f, face, openGap)
        step(0.9f, upGaze, face, openGap)
        step(0.9f, 0.5f, face, openGap)
        step(0.7f, downGaze, face, openGap)
        step(0.9f, 0.5f, face, openGap)
        step(0.7f, downGaze, face, openGap)
        step(0.9f, 0.5f, face, openGap)
        step(closedOpen, 0.6f, face, closedGap)
        step(0.9f, 0.5f, face, openGap)
        step(closedOpen, 0.6f, face, closedGap)
        step(0.9f, 0.5f, face, openGap)
    }

    @Test
    fun `lines are set from the measured eyes`() {
        run()
        val result = calibration.result(Tuning()) as Calibration.Result.Ok
        assertEquals(0.5f, result.measured.upReach, 0.001f)
        assertEquals(0.2f, result.tuning.gaze.lookStrength, 0.001f)
        assertEquals(0.65f, result.tuning.blink.closedBelow, 0.001f)
        assertEquals(0.775f, result.tuning.blink.openAbove, 0.001f)
    }

    @Test
    fun `every step prompt is announced in order`() {
        run()
        assertEquals(Calibration.Step.entries.drop(1), spoken)
    }

    @Test
    fun `no look up keeps the current look line and still sets the blink lines`() {
        run(upGaze = 0.48f)
        val before = Tuning()
        val result = calibration.result(before) as Calibration.Result.Ok
        assertEquals(before.gaze.lookStrength, result.tuning.gaze.lookStrength, 0.0001f)
        assertEquals(0.65f, result.tuning.blink.closedBelow, 0.001f)
    }

    @Test
    fun `one good look up is enough`() {
        calibration.start(time)
        step(0.9f, 0.5f)
        step(0.9f, 0f)
        step(0.9f, 0.5f)
        step(0.9f, 0.48f)
        step(0.9f, 0.5f)
        step(0.7f, 0.9f)
        step(0.9f, 0.5f)
        step(0.7f, 0.9f)
        step(0.9f, 0.5f)
        step(0.4f, 0.6f)
        step(0.9f, 0.5f)
        step(0.4f, 0.6f)
        step(0.9f, 0.5f)
        val result = calibration.result(Tuning()) as Calibration.Result.Ok
        assertEquals(0.2f, result.tuning.gaze.lookStrength, 0.001f)
    }

    @Test
    fun `eyes that never close fail and say so`() {
        run(closedOpen = 0.85f)
        val result = calibration.result(Tuning()) as Calibration.Result.Failed
        assertTrue(result.reason.startsWith("Closed eyes not seen"))
    }

    @Test
    fun `no face fails`() {
        run(face = false)
        assertTrue(calibration.result(Tuning()) is Calibration.Result.Failed)
    }

    @Test
    fun `other settings are kept`() {
        run()
        val before = Tuning()
        val after = (calibration.result(before) as Calibration.Result.Ok).tuning
        assertEquals(before.blink.minBlinkMs, after.blink.minBlinkMs)
        assertEquals(before.moveByEyes, after.moveByEyes)
    }

    @Test
    fun `lid gap lines are set when the gap is read`() {
        run(openGap = 0.30f, closedGap = 0.06f)
        val blink = (calibration.result(Tuning()) as Calibration.Result.Ok).tuning.blink
        assertEquals(0.084f, blink.shapeClosedBelow!!, 0.001f)
        assertEquals(0.132f, blink.shapeOpenAbove!!, 0.001f)
    }

    @Test
    fun `a squeezed frame or two does not set the lid gap line`() {
        calibration.start(time)
        listOf(0.5f, 0f, 0.5f, 0f, 0.5f, 0.9f, 0.5f, 0.9f, 0.5f).forEachIndexed { i, gaze ->
            step(if (i == 5 || i == 7) 0.7f else 0.9f, gaze, gap = 0.30f)
        }
        // Each close: held at a gap of 0.12, with a hard squeeze reading 0.01 for three frames.
        repeat(2) { close ->
            repeat(50) { frame ->
                val gap = if (frame in 30..32) 0.01f else 0.12f
                calibration.onSample(EyeSample(time, true, 0.4f, 0.4f, gaze = Dot(0f, 0.6f), dots = FaceDots(emptyList(), emptyList(), 0.75f, gap, gap)))
                time += 50
            }
            step(0.9f, 0.5f, gap = 0.30f)
        }
        val result = calibration.result(Tuning()) as Calibration.Result.Ok
        assertEquals(0.12f, result.measured.closedGap!!, 0.001f)
        assertEquals(0.138f, result.tuning.blink.shapeClosedBelow!!, 0.001f)
    }

    @Test
    fun `open frames before the person closes do not set the lid gap line`() {
        calibration.start(time)
        listOf(0.5f, 0f, 0.5f, 0f, 0.5f, 0.9f, 0.5f, 0.9f, 0.5f).forEachIndexed { i, gaze ->
            step(if (i == 5 || i == 7) 0.7f else 0.9f, gaze, gap = 0.30f)
        }
        // "Close your eyes" takes about 1.3 s to say, so each close step starts with open eyes.
        repeat(2) {
            repeat(50) { frame ->
                val shut = frame >= 35
                val gap = if (shut) 0.10f else 0.30f
                calibration.onSample(EyeSample(time, true, if (shut) 0.4f else 0.9f, if (shut) 0.4f else 0.9f, gaze = Dot(0f, 0.6f), dots = FaceDots(emptyList(), emptyList(), 0.75f, gap, gap)))
                time += 50
            }
            step(0.9f, 0.5f, gap = 0.30f)
        }
        val result = calibration.result(Tuning()) as Calibration.Result.Ok
        assertEquals(0.10f, result.measured.closedGap!!, 0.001f)
        assertEquals(0.12f, result.tuning.blink.shapeClosedBelow!!, 0.001f)
    }

    @Test
    fun `no lid gap reading leaves the gap check off`() {
        run()
        val blink = (calibration.result(Tuning()) as Calibration.Result.Ok).tuning.blink
        assertEquals(null, blink.shapeClosedBelow)
    }

    @Test
    fun `look down line is set from the measured look down`() {
        run(downGaze = 0.9f)
        val gaze = (calibration.result(Tuning()) as Calibration.Result.Ok).tuning.gaze
        assertEquals(0.12f, gaze.downStrength!!, 0.001f)
    }

    @Test
    fun `no look down turns looking down off but keeps the rest`() {
        run(downGaze = 0.52f)
        val result = calibration.result(Tuning()) as Calibration.Result.Ok
        assertEquals(null, result.tuning.gaze.downStrength)
        assertEquals(0.2f, result.tuning.gaze.lookStrength, 0.001f)
    }

    @Test
    fun `one weak close does not set the lines`() {
        calibration.start(time)
        step(0.9f, 0.5f, gap = 0.30f)
        step(0.9f, 0f, gap = 0.30f)
        step(0.9f, 0.5f, gap = 0.30f)
        step(0.9f, 0f, gap = 0.30f)
        step(0.9f, 0.5f, gap = 0.30f)
        step(0.7f, 0.9f, gap = 0.30f)
        step(0.9f, 0.5f, gap = 0.30f)
        step(0.7f, 0.9f, gap = 0.30f)
        step(0.9f, 0.5f, gap = 0.30f)
        step(0.4f, 0.6f, gap = 0.06f)
        step(0.9f, 0.5f, gap = 0.30f)
        // Second close only half done.
        step(0.7f, 0.6f, gap = 0.22f)
        step(0.9f, 0.5f, gap = 0.30f)
        val result = calibration.result(Tuning()) as Calibration.Result.Ok
        assertEquals(0.4f, result.measured.closedOpen, 0.001f)
        assertEquals(0.06f, result.measured.closedGap!!, 0.001f)
    }

    @Test
    fun `iris look down line is half the measured iris drop`() {
        calibration.start(time)
        val rest = 0.10f
        step(0.9f, 0.5f, iris = rest)
        step(0.9f, 0f, iris = rest)
        step(0.9f, 0.5f, iris = rest)
        step(0.9f, 0f, iris = rest)
        step(0.9f, 0.5f, iris = rest)
        step(0.7f, 0.55f, iris = 0.18f)
        step(0.9f, 0.5f, iris = rest)
        step(0.7f, 0.55f, iris = 0.16f)
        step(0.9f, 0.5f, iris = rest)
        step(0.4f, 0.6f, iris = rest)
        step(0.9f, 0.5f, iris = rest)
        step(0.4f, 0.6f, iris = rest)
        step(0.9f, 0.5f, iris = rest)
        val result = calibration.result(Tuning()) as Calibration.Result.Ok
        assertEquals(0.06f, result.measured.irisDownReach!!, 0.0001f)
        assertEquals(0.018f, result.tuning.gaze.irisDownStrength!!, 0.0001f)
        // A small measured look down never puts the line under the resting drift.
        assertTrue(result.tuning.gaze.irisDownStrength!! >= Calibration.MIN_IRIS_DOWN_LINE)
        // The blendshape look down barely moved, so it stays off.
        assertEquals(null, result.tuning.gaze.downStrength)
    }

    @Test
    fun `the screen steps run in order from 1 to 5 and end on done`() {
        val stages = Calibration.Step.entries.map { it.stage }
        assertEquals(stages.sorted(), stages)
        assertEquals((1..Calibration.STAGES).toList(), stages.distinct())
        assertEquals(Calibration.STAGES, Calibration.Step.Done.stage)
    }

    @Test
    fun `the whole calibration takes one step length for each step before done`() {
        assertEquals(2_500L * 12, calibration.totalMs)
    }
}
