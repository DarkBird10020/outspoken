package com.outspoken.control

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkSettings
import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import com.outspoken.help.HelpStep
import com.outspoken.help.HelpTrigger
import com.outspoken.scan.GazeStepper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The eyes while phone control is on: looks move, a close presses, the help hold still works. */
class ControlEyesTest {

    private val eyes = ControlEyes(BlinkDetector(BlinkSettings(minBlinkMs = 400, maxBlinkMs = 1_950)), GazeStepper(), HelpTrigger())
    private var time = 10_000L

    private fun frames(ms: Long, open: Float = 0.95f, gazeY: Float = 0f): List<ControlInput> {
        val out = mutableListOf<ControlInput>()
        val end = time + ms
        while (time < end) {
            out += eyes.onSample(EyeSample(time, true, open, open, gaze = Dot(0f, gazeY)))
            time += 33
        }
        return out
    }

    @Test
    fun `a close presses the lit button`() {
        frames(500)
        assertEquals(listOf(ControlInput.Select), frames(600, open = 0.05f) + frames(200))
    }

    @Test
    fun `looking down moves to the next button and up to the one before`() {
        frames(500)
        assertEquals(listOf(ControlInput.Next), frames(500, gazeY = 0.5f) + frames(1_100))
        assertEquals(listOf(ControlInput.Previous), frames(500, gazeY = -0.7f) + frames(1_100))
    }

    @Test
    fun `the help hold calls for help and presses nothing`() {
        frames(500)
        val out = frames(2_300, open = 0.05f) + frames(500) + frames(600, open = 0.05f) + frames(300)
        assertEquals(listOf(ControlInput.Help(HelpStep.HoldReached), ControlInput.Help(HelpStep.Alarm)), out)
    }

    @Test
    fun `a look down just before a close is taken back`() {
        frames(500)
        val out = mutableListOf<ControlInput>()
        repeat(60) { if (ControlInput.Next !in out) out += frames(33, gazeY = 0.5f) }
        out += frames(500, open = 0.05f, gazeY = 0.5f) + frames(300)
        assertEquals(listOf(ControlInput.Next, ControlInput.Previous, ControlInput.Select), out)
    }

    @Test
    fun `with blink only the lit button moves on by itself`() {
        eyes.moveByEyes = false
        eyes.scanMs = 1_000
        val out = frames(3_100)
        assertTrue(out.count { it == ControlInput.Next } in 3..4)
        assertFalse(ControlInput.Select in out)
    }
}
