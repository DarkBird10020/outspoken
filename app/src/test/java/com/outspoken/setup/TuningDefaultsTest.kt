package com.outspoken.setup

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The default blink rules against what the phone logs of 2026-10-10 showed: unprompted closes
 * lasted 74 to 242 ms and must never pick; a held close must.
 */
class TuningDefaultsTest {

    private val detector = BlinkDetector(Tuning().blink)
    private var time = 10_000L

    private fun frames(ms: Long, open: Float): List<BlinkEvent> {
        val events = mutableListOf<BlinkEvent>()
        val end = time + ms
        while (time < end) {
            detector.onSample(EyeSample(time, true, open, open))?.let(events::add)
            time += 33
        }
        return events
    }

    private fun picks(events: List<BlinkEvent>) = events.filterIsInstance<BlinkEvent.Blink>()

    @Test
    fun `blinks as long as the unprompted ones on the phone never pick`() {
        frames(1_000, open = 0.95f)
        listOf(74L, 112L, 162L, 200L, 206L, 242L).forEach { closedMs ->
            val events = frames(closedMs, open = 0.05f) + frames(1_500, open = 0.95f)
            assertEquals("a $closedMs ms close picked", emptyList<BlinkEvent.Blink>(), picks(events))
        }
    }

    @Test
    fun `two quick blinks in a row do not pick`() {
        frames(1_000, open = 0.95f)
        val events = frames(150, open = 0.05f) + frames(150, open = 0.95f) + frames(120, open = 0.05f) + frames(1_000, open = 0.95f)
        assertTrue(picks(events).isEmpty())
    }

    @Test
    fun `holding the eyes shut picks`() {
        frames(1_000, open = 0.95f)
        val events = frames(600, open = 0.05f) + frames(300, open = 0.95f)
        assertEquals(1, picks(events).size)
    }

    @Test
    fun `the pick hold stays above every unprompted blink`() {
        assertTrue(Tuning().blink.minBlinkMs >= MIN_PICK_HOLD_MS)
        assertTrue(MIN_PICK_HOLD_MS > 242)
    }

    @Test
    fun `hand signs are off until switched on, with every sign ready`() {
        assertEquals(false, Tuning().handGestures)
        assertEquals(com.outspoken.hand.HandSign.entries.toSet(), Tuning().handSigns)
    }

    @Test
    fun `cards start in English`() {
        assertEquals(com.outspoken.conversation.AppLanguage.English, Tuning().language)
        assertEquals(false, Tuning().autoLanguage)
    }
}
