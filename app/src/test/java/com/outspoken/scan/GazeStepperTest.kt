package com.outspoken.scan

import com.outspoken.eye.Dot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeStepperTest {

    private val stepper = GazeStepper(GazeSettings(lookStrength = 0.45f, downStrength = 0.3f, lookHoldMs = 250))
    private var time = 0L

    /** Feeds one frame per 50 ms for [ms] and returns the steps seen. Up is negative y. */
    private fun look(ms: Long, x: Float = 0f, y: Float = 0f, eyesOpen: Boolean = true): List<GazeStep> {
        val steps = mutableListOf<GazeStep>()
        val end = time + ms
        while (time < end) {
            stepper.onSample(Dot(x, y), eyesOpen, time)?.let(steps::add)
            time += 50
        }
        return steps
    }

    @Test
    fun `resting gaze does not move anything`() {
        assertEquals(emptyList<GazeStep>(), look(2_000))
    }

    @Test
    fun `looking down moves the highlight down once`() {
        look(500)
        assertEquals(listOf(GazeStep.Next), look(1_000, y = 0.4f))
    }

    @Test
    fun `looking up moves the highlight up once`() {
        look(500)
        assertEquals(listOf(GazeStep.Previous), look(1_000, y = -0.7f))
    }

    @Test
    fun `looking down can be switched off`() {
        stepper.settings = stepper.settings.copy(downStrength = null)
        look(500)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = 0.6f))
    }

    @Test
    fun `looking sideways does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(500, x = 0.6f) + look(500, x = -0.6f))
    }

    @Test
    fun `small up and down movement from reading the cards does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = -0.3f) + look(300) + look(1_000, y = 0.2f))
    }

    @Test
    fun `a glance shorter than the hold time does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(200, y = -0.7f) + look(300) + look(200, y = 0.5f))
    }

    @Test
    fun `each look needs a return to rest`() {
        look(500)
        val steps = look(500, y = 0.5f) + look(300) + look(500, y = 0.5f)
        assertEquals(listOf(GazeStep.Next, GazeStep.Next), steps)
    }

    @Test
    fun `shut eyes never step`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(500, y = 0.6f, eyesOpen = false) + look(500, y = -0.7f, eyesOpen = false))
    }

    @Test
    fun `gaze is measured from where the eyes rest`() {
        // Phone below eye level: resting gaze already points down.
        look(1_000, y = 0.6f)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = 0.5f))
        assertEquals(listOf(GazeStep.Previous), look(500, y = -0.1f))
    }

    @Test
    fun `holding a look steps once and does not move the rest`() {
        look(500)
        assertEquals(listOf(GazeStep.Previous), look(4_000, y = -0.7f))
        // Back at the old rest: nothing, because the rest did not move to the held look.
        assertEquals(emptyList<GazeStep>(), look(1_000))
    }

    @Test
    fun `coming back from a look is not a look the other way`() {
        look(500)
        // Look down, then the eyes overshoot upward on the way back.
        val steps = look(400, y = 0.5f) + look(450, y = -0.6f) + look(500)
        assertEquals(listOf(GazeStep.Next), steps)
    }

    @Test
    fun `a real look the other way after the rebound time still steps`() {
        look(500)
        val steps = look(400, y = 0.5f) + look(1_000) + look(500, y = -0.7f)
        assertEquals(listOf(GazeStep.Next, GazeStep.Previous), steps)
    }

    @Test
    fun `a measured rest is the centre from the first frame`() {
        stepper.restAt(0.5f)
        assertEquals(0.5f, stepper.restGaze!!, 0.0001f)
        // Still eyes at the measured rest do nothing; a look down from there steps.
        assertEquals(emptyList<GazeStep>(), look(500, y = 0.5f))
        assertEquals(listOf(GazeStep.Next), look(500, y = 0.85f))
    }

    @Test
    fun `with the iris line set, a look down is read from the iris`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.04f)
        val steps = mutableListOf<GazeStep>()
        repeat(10) { stepper.onSample(Dot(0f, 0.6f), true, time, irisY = 0.10f); time += 50 }
        // Looking down: blendshape barely moves, iris drops 0.06; the lid gap keeps it "open".
        repeat(10) { stepper.onSample(Dot(0f, 0.65f), true, time, irisY = 0.16f)?.let(steps::add); time += 50 }
        assertEquals(listOf(GazeStep.Next), steps)
        assertTrue(stepper.irisLooksDown(0.16f))
    }

    @Test
    fun `with the iris line set, a small iris movement does nothing`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.04f)
        val steps = mutableListOf<GazeStep>()
        repeat(10) { stepper.onSample(Dot(0f, 0.6f), true, time, irisY = 0.10f); time += 50 }
        repeat(20) { stepper.onSample(Dot(0f, 0.9f), true, time, irisY = 0.12f)?.let(steps::add); time += 50 }
        assertEquals(emptyList<GazeStep>(), steps)
    }

    @Test
    fun `with the iris line set but no iris reading, the blendshape look down still works`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.012f)
        look(500)
        assertEquals(listOf(GazeStep.Next), look(1_000, y = 0.4f))
    }

    @Test
    fun `shut eyes read as a huge iris drop are not a look down`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.012f)
        repeat(10) { stepper.onSample(Dot(0f, 0.6f), true, time, irisY = -0.03f); time += 50 }
        // Phone log: eyes closed read an iris drop of about 0.31.
        assertFalse(stepper.irisLooksDown(0.31f))
        val steps = mutableListOf<GazeStep>()
        repeat(10) { stepper.onSample(Dot(0f, 0.97f), false, time, irisY = 0.31f)?.let(steps::add); time += 50 }
        assertEquals(emptyList<GazeStep>(), steps)
        assertTrue(stepper.irisLooksDown(0.0f))
    }

    @Test
    fun `shut eyes never step even when the iris reads in the look down range`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.017f)
        repeat(10) { stepper.onSample(Dot(0f, 0.5f), true, time, irisY = -0.03f); time += 50 }
        // Phone log 00:46:18: a real close read an iris drop of +0.039 from rest.
        val steps = mutableListOf<GazeStep>()
        repeat(12) { stepper.onSample(Dot(0f, 0.7f), false, time, irisY = 0.009f)?.let(steps::add); time += 50 }
        assertEquals(emptyList<GazeStep>(), steps)
    }

    @Test
    fun `a drifting resting iris is followed instead of stepping again and again`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.02f)
        repeat(10) { stepper.onSample(Dot(0f, 0.4f), true, time, irisY = -0.03f); time += 50 }
        // Phone log 00:51: resting eyes drift from -0.034 to -0.013 without a look.
        val steps = mutableListOf<GazeStep>()
        for (iris in listOf(-0.025f, -0.02f, -0.016f, -0.013f)) {
            repeat(30) { stepper.onSample(Dot(0f, 0.4f), true, time, irisY = iris)?.let(steps::add); time += 50 }
        }
        assertEquals(emptyList<GazeStep>(), steps)
        assertEquals(-0.013f, stepper.restIrisDrop!!, 0.004f)
    }

    @Test
    fun `single frame wobble across the line does not step`() {
        look(500)
        // Phone log 01:16: wobble just over the up line every few seconds.
        val steps = mutableListOf<GazeStep>()
        repeat(40) { n -> stepper.onSample(Dot(0f, if (n % 3 == 0) -0.5f else -0.05f), true, time)?.let(steps::add); time += 50 }
        assertEquals(emptyList<GazeStep>(), steps)
    }

    @Test
    fun `many looks down in a row all register`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.02f)
        repeat(10) { stepper.onSample(Dot(0f, 0.5f), true, time, irisY = -0.03f); time += 50 }
        val steps = mutableListOf<GazeStep>()
        repeat(8) {
            // Look down (iris 0.035 below rest) for 0.8 s, back to centre for 0.8 s.
            repeat(16) { stepper.onSample(Dot(0f, 0.55f), true, time, irisY = 0.005f)?.let(steps::add); time += 50 }
            repeat(16) { stepper.onSample(Dot(0f, 0.5f), true, time, irisY = -0.03f)?.let(steps::add); time += 50 }
        }
        assertEquals(List(8) { GazeStep.Next }, steps)
    }

    @Test
    fun `a wobble under the line during a look down does not stop it registering`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.02f)
        repeat(10) { stepper.onSample(Dot(0f, 0.5f), true, time, irisY = -0.03f); time += 50 }
        val steps = mutableListOf<GazeStep>()
        // Looking down, but every other frame dips back under the line (above half of it).
        repeat(20) { n -> stepper.onSample(Dot(0f, 0.5f), true, time, irisY = if (n % 2 == 0) 0f else -0.018f)?.let(steps::add); time += 50 }
        assertEquals(listOf(GazeStep.Next), steps)
    }

    @Test
    fun `hovering around the line steps only once`() {
        stepper.settings = stepper.settings.copy(irisDownStrength = 0.02f)
        repeat(10) { stepper.onSample(Dot(0f, 0.5f), true, time, irisY = -0.03f); time += 50 }
        val steps = mutableListOf<GazeStep>()
        // Drop wobbling between 0.9 and 1.3 of the line for three seconds.
        repeat(60) { n -> stepper.onSample(Dot(0f, 0.5f), true, time, irisY = if (n % 4 < 2) -0.004f else -0.012f)?.let(steps::add); time += 50 }
        assertEquals(listOf(GazeStep.Next), steps)
    }

    @Test
    fun `just after the face comes back nothing steps while the rest is learned`() {
        look(500)
        stepper.forgetRest()
        // First frames after the face is found are still moving; the eyes then settle at 0.3.
        val steps = look(200, y = 0.9f) + look(1_500, y = 0.3f)
        assertEquals(emptyList<GazeStep>(), steps)
        assertEquals(listOf(GazeStep.Previous), look(600, y = -0.3f))
    }
}
