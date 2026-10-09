package com.outspoken.scan

import com.outspoken.eye.Dot
import org.junit.Assert.assertEquals
import org.junit.Test

class GazeStepperTest {

    private val stepper = GazeStepper(GazeSettings(lookStrength = 0.45f, lookHoldMs = 250))
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
    fun `looking up moves to the next card once`() {
        look(500)
        assertEquals(listOf(GazeStep.Next), look(1_000, y = -0.7f))
    }

    @Test
    fun `looking down does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = 0.4f))
    }

    @Test
    fun `looking sideways does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(500, x = 0.6f) + look(500, x = -0.6f))
    }

    @Test
    fun `a small look up from reading the cards does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = -0.3f))
    }

    @Test
    fun `a glance shorter than the hold time does nothing`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(200, y = -0.7f))
    }

    @Test
    fun `each look needs a return to rest`() {
        look(500)
        val steps = look(500, y = -0.7f) + look(300) + look(500, y = -0.7f)
        assertEquals(listOf(GazeStep.Next, GazeStep.Next), steps)
    }

    @Test
    fun `shut eyes never step`() {
        look(500)
        assertEquals(emptyList<GazeStep>(), look(500, y = -0.7f, eyesOpen = false))
    }

    @Test
    fun `gaze is measured from where the eyes rest`() {
        // Phone below eye level: resting gaze already points down.
        look(1_000, y = 0.6f)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = 0.5f))
        assertEquals(listOf(GazeStep.Next), look(500, y = -0.1f))
    }

    @Test
    fun `holding up for long becomes the new rest`() {
        look(500)
        look(3_500, y = -0.7f)
        assertEquals(emptyList<GazeStep>(), look(1_000, y = -0.7f))
    }
}
