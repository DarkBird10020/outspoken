package com.outspoken.conversation

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkSettings
import com.outspoken.eye.EyeSample
import com.outspoken.help.HelpStep
import com.outspoken.scan.Scanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eyes held shut, opened, then one blink: the alarm, and no card is said. */
class ConversationHelpTest {

    private val said = mutableListOf<String>()
    private val steps = mutableListOf<HelpStep>()
    private val controller = ConversationController(
        speak = { said += it },
        detector = BlinkDetector(BlinkSettings(minBlinkMs = 400, maxBlinkMs = 1_500)),
        scanner = Scanner(intervalMs = 1_200),
        onHelp = { steps += it },
    )
    private var time = 10_000L

    private fun frames(ms: Long, open: Float) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, true, open, open))
            time += 33
        }
    }

    @Test
    fun `hold, open, blink raises the alarm without saying a card`() {
        frames(300, open = 0.95f)
        frames(2_300, open = 0.05f)
        assertEquals(listOf(HelpStep.HoldReached), steps)
        frames(500, open = 0.95f)
        frames(600, open = 0.05f)
        frames(200, open = 0.95f)
        assertEquals(listOf(HelpStep.HoldReached, HelpStep.Alarm), steps)
        assertTrue(said.isEmpty())
    }

    @Test
    fun `a normal pick does not raise the alarm`() {
        frames(300, open = 0.95f)
        frames(600, open = 0.05f)
        frames(200, open = 0.95f)
        assertTrue(steps.isEmpty())
        assertEquals(listOf("I need water"), said)
    }
}
