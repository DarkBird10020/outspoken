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
    private fun step(open: Float, gazeY: Float, face: Boolean = true, gap: Float? = null) {
        repeat(50) {
            val dots = gap?.let { FaceDots(emptyList(), emptyList(), 0.75f, it, it) }
            val sample = if (face) EyeSample(time, true, open, open, gaze = Dot(0f, gazeY), dots = dots) else EyeSample(time, false)
            calibration.onSample(sample)?.let(spoken::add)
            time += 50
        }
    }

    /** A person whose eyes rest at 0.9 open, gaze 0.5, look up to 0.0 and close to 0.4. */
    private fun run(upGaze: Float = 0f, closedOpen: Float = 0.4f, face: Boolean = true, openGap: Float? = null, closedGap: Float? = null) {
        calibration.start(time)
        step(0.9f, 0.5f, face, openGap)
        step(0.9f, upGaze, face, openGap)
        step(0.9f, 0.5f, face, openGap)
        step(0.9f, upGaze, face, openGap)
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
        assertEquals(0.132f, blink.shapeClosedBelow!!, 0.001f)
        assertEquals(0.18f, blink.shapeOpenAbove!!, 0.001f)
    }

    @Test
    fun `no lid gap reading leaves the gap check off`() {
        run()
        val blink = (calibration.result(Tuning()) as Calibration.Result.Ok).tuning.blink
        assertEquals(null, blink.shapeClosedBelow)
    }
}
