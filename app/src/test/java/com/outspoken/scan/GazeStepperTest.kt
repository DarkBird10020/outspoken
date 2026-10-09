package com.outspoken.scan

import com.outspoken.eye.Dot
import org.junit.Assert.assertEquals
import org.junit.Test

class GazeStepperTest {

    private val stepper = GazeStepper(GazeSettings(lookStrength = 0.25f, lookHoldMs = 250))
    private var time = 0L

    /** Feeds one frame per 50 ms for [ms] and returns the steps seen. */
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
    fun `looking down moves to the next card once`() {
        look(500)
        assertEquals(listOf(GazeStep.Next), look(1_000, y = 0.4f))
    }

    @Test
    fun `looking up moves back`() {
        look(500)
        assertEquals(listOf(GazeStep.Previous), look(500, y = -0.4f))
    }

    @Test
    fun `looking sideways does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(500, x = 0.6f) + look(500, x = -0.6f))
    }

    @Test
    fun `a glance shorter than the hold time does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(200, y = 0.4f))
    }

    @Test
    fun `each look needs a return to rest`() {
        look(500)
        val steps = look(500, y = 0.4f) + look(300) + look(500, y = 0.4f)
        assertEquals(listOf(GazeStep.Next, GazeStep.Next), steps)
    }

    @Test
    fun `closing eyes is not a look down`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(500, y = 0.5f, eyesOpen = false))
    }

    @Test
    fun `gaze is measured from where the eyes rest`() {
        // Phone below eye level: resting gaze already points down.
        look(1_000, y = 0.3f)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = 0.35f))
        assertEquals(listOf(GazeStep.Next), look(500, y = 0.7f))
    }

    @Test
    fun `holding one direction for long becomes the new rest`() {
        look(500)
        look(3_500, y = 0.4f)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = 0.4f))
    }
}
