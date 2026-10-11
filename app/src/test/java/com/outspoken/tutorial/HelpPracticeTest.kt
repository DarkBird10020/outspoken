package com.outspoken.tutorial

import com.outspoken.blink.BlinkDetector
import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpPracticeTest {

    private val said = mutableListOf<String>()
    private val logged = mutableListOf<String>()
    private var cues = 0
    private val practice = HelpPractice(
        BlinkDetector(),
        log = { area, message -> logged += "$area: $message" },
        speak = { said += it },
        cue = { cues++ },
    )
    private var time = 10_000L

    private val ui get() = practice.ui.value

    private fun frames(ms: Long, open: Float = 0.95f) {
        val end = time + ms
        while (time < end) {
            practice.onSample(EyeSample(time, true, open, open))
            time += 33
        }
    }

    private fun start() {
        practice.start(time)
        frames(500)
    }

    @Test
    fun `the hold fills up while the eyes stay shut`() {
        start()
        frames(1_000, open = 0.05f)
        assertEquals(HelpStage.CloseEyes, ui.stage)
        assertTrue(ui.holdProgress in 0.4f..0.6f)
    }

    @Test
    fun `holding two seconds beeps and asks for the blink`() {
        start()
        frames(2_200, open = 0.05f)
        assertEquals(HelpStage.OpenAndBlink, ui.stage)
        assertEquals(1, cues)
        assertEquals("Now open your eyes, then blink twice quickly.", said.last())
    }

    @Test
    fun `the hold then a blink finishes the practice without calling for help`() {
        start()
        frames(2_200, open = 0.05f)
        frames(400)
        frames(500, open = 0.05f)
        frames(300)
        assertEquals(HelpStage.Done, ui.stage)
        assertTrue(said.last().startsWith("Well done."))
        // Every line is marked as practice, so the logs never read as a real call for help.
        assertTrue(logged.isNotEmpty())
        assertTrue(logged.all { it.startsWith("tutorial: ") })
    }

    @Test
    fun `no blink within five seconds of the hold starts again`() {
        start()
        frames(2_200, open = 0.05f)
        frames(5_500)
        assertEquals(HelpStage.CloseEyes, ui.stage)
        assertTrue(said.last().startsWith("Too slow."))
    }

    @Test
    fun `a normal blink does nothing`() {
        start()
        frames(500, open = 0.05f)
        frames(500)
        assertEquals(HelpStage.CloseEyes, ui.stage)
        assertEquals(0, cues)
    }
}
