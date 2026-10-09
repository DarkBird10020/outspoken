package com.outspoken.conversation

import com.outspoken.eye.EyeSample
import com.outspoken.scan.Scanner
import com.outspoken.suggest.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The visitor's question: shown on the "Heard" card and sent to the model with the conversation. */
class ConversationHeardTest {

    private val said = mutableListOf<String>()
    private val requests = mutableListOf<List<Turn>>()
    private val controller = ConversationController(
        speak = { said += it },
        scanner = Scanner(intervalMs = 1_200),
        requestReplies = { _, turns ->
            requests += turns
            true
        },
    )
    private var time = 10_000L

    private fun frames(ms: Long, open: Float = 0.95f) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, true, open, open))
            time += 33
        }
    }

    @Test
    fun `a heard question shows and asks for replies`() {
        controller.onHeard("Are you in pain?", time)
        assertEquals("Are you in pain?", controller.ui.value.heard)
        assertEquals(listOf(Turn(fromListener = true, text = "Are you in pain?")), requests.single())
    }

    @Test
    fun `the answer goes to the model after the question`() {
        controller.onHeard("Are you in pain?", time)
        controller.onTap(1, time)
        assertEquals(
            listOf(Turn(true, "Are you in pain?"), Turn(false, "I am in pain")),
            requests.last(),
        )
    }

    @Test
    fun `answering clears the question`() {
        controller.onHeard("Are you thirsty?", time)
        controller.onTap(0, time)
        assertNull(controller.ui.value.heard)
    }

    @Test
    fun `a question while the phone speaks is ignored`() {
        frames(100)
        controller.onTap(0, time)
        controller.onHeard("Did you say water?", time)
        assertEquals(1, requests.size)
        assertNull(controller.ui.value.heard)
    }
}
