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
        assertEquals(0.25f, result.tuning.gaze.lookStrength, 0.001f)
        assertEquals(0.65f, result.tuning.blink.closedBelow, 0.001f)
        assertEquals(0.775f, result.tuning.blink.openAbove, 0.001f)
    }

    @Test
    fun `every step prompt is announced in order`() {
        run()
        assertEquals(Calibration.Step.entries.drop(1), spoken)
    }

    @Test
    fun `no look up fails and says so`() {
        run(upGaze = 0.48f)
        val result = calibration.result(Tuning())
        assertTrue(result is Calibration.Result.Failed)
        assertTrue((result as Calibration.Result.Failed).reason.startsWith("Look up not seen"))
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
    fun `no lid gap reading leaves the gap check off`() {
        run()
        val blink = (calibration.result(Tuning()) as Calibration.Result.Ok).tuning.blink
        assertEquals(null, blink.shapeClosedBelow)
    }

    @Test
    fun `look down line is set from the measured look down`() {
        run(downGaze = 0.9f)
        val gaze = (calibration.result(Tuning()) as Calibration.Result.Ok).tuning.gaze
        assertEquals(0.14f, gaze.downStrength!!, 0.001f)
    }

    @Test
    fun `no look down turns looking down off but keeps the rest`() {
        run(downGaze = 0.52f)
        val result = calibration.result(Tuning()) as Calibration.Result.Ok
        assertEquals(null, result.tuning.gaze.downStrength)
        assertEquals(0.25f, result.tuning.gaze.lookStrength, 0.001f)
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
        assertEquals(0.021f, result.tuning.gaze.irisDownStrength!!, 0.0001f)
        // A small measured look down never puts the line under the resting drift.
        assertTrue(result.tuning.gaze.irisDownStrength!! >= Calibration.MIN_IRIS_DOWN_LINE)
        // The blendshape look down barely moved, so it stays off.
        assertEquals(null, result.tuning.gaze.downStrength)
    }
}
