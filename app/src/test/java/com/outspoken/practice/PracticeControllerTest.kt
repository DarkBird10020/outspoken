package com.outspoken.practice

import com.outspoken.blink.BlinkSettings
import com.outspoken.eye.EyeSample
import com.outspoken.setup.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeControllerTest {

    private var calibratedTuning: Tuning? = null
    private var spokenMessage: String? = null

    private val controller = PracticeController(
        initialTuning = Tuning(blink = BlinkSettings(minBlinkMs = 300, maxBlinkMs = 900)),
        speak = { spokenMessage = it },
        onCalibrated = { calibratedTuning = it },
    )
    private var time = 1_000L

    private fun sample(left: Float, right: Float = left, faceFound: Boolean = true) =
        EyeSample(time, faceFound = faceFound, leftOpen = left, rightOpen = right)

    private fun feed(ms: Long, sampleProvider: () -> EyeSample) {
        val end = time + ms
        while (time < end) {
            controller.onSample(sampleProvider())
            time += 33
        }
    }

    private fun blink(durationMs: Long) {
        feed(300) { sample(0.85f) }
        feed(durationMs) { sample(0.15f) }
        feed(300) { sample(0.85f) }
    }

    @Test
    fun `initial state shows 0 caught with prompt`() {
        val ui = controller.ui.value
        assertEquals(0, ui.caught)
        assertEquals(3, ui.needed)
        assertEquals(0, ui.starAt)
        assertEquals("Blink when the star lights up", ui.progressNote)
    }

    @Test
    fun `deliberate blink catches star and advances target`() {
        blink(450)
        val ui = controller.ui.value
        assertEquals(1, ui.caught)
        assertEquals(1, ui.starAt)
        assertEquals("First star caught. Blink again.", spokenMessage)
        assertTrue(calibratedTuning != null)
    }

    @Test
    fun `three blinks complete practice round`() {
        blink(450)
        blink(450)
        blink(450)
        val ui = controller.ui.value
        assertEquals(3, ui.caught)
        assertEquals("Ready! Tap start talking below", ui.progressNote)
        assertEquals("Practice complete. You are ready to talk.", spokenMessage)
    }

    @Test
    fun `fast blink does not catch star`() {
        blink(120)
        val ui = controller.ui.value
        assertEquals(0, ui.caught)
        assertEquals(0, ui.starAt)
    }

    @Test
    fun `reset restarts practice round`() {
        blink(450)
        assertEquals(1, controller.ui.value.caught)
        controller.reset()
        assertEquals(0, controller.ui.value.caught)
        assertEquals(0, controller.ui.value.starAt)
    }
}
