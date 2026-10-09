package com.outspoken.log

import com.outspoken.blink.BlinkDetector
import com.outspoken.conversation.ConversationController
import com.outspoken.eye.EyeSample
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lines that explain a run on the phone: why a blink counted or not, and what was said. */
class LogLinesTest {

    private val lines = mutableListOf<String>()
    private val log = EventLog { area, message -> lines += "$area: $message" }
    private val controller = ConversationController(
        speak = {},
        detector = BlinkDetector(log = log),
        log = log,
    )
    private var time = 10_000L

    private fun frames(ms: Long, open: Float, faceFound: Boolean = true, yaw: Float = 0f) {
        val end = time + ms
        while (time < end) {
            controller.onSample(EyeSample(time, faceFound, open, open, yawDeg = yaw))
            time += 33
        }
    }

    private fun assertLogged(line: String) =
        assertTrue("missing \"$line\" in\n${lines.joinToString("\n")}", line in lines)

    @Test
    fun `a long blink logs what was chosen and said`() {
        frames(100, open = 0.95f)
        frames(1_100, open = 0.05f)
        frames(100, open = 0.95f)
        assertLogged("blink: face found")
        assertTrue(lines.any { it.startsWith("blink: eyes shut (left 0.05, right 0.05") })
        assertTrue(lines.any { it.startsWith("blink: long blink") })
        assertLogged("scan: long blink chose \"I need water\"")
        assertLogged("scan: say \"I need water\"")
    }

    @Test
    fun `a short blink logs where the highlight went`() {
        frames(100, open = 0.95f)
        frames(500, open = 0.05f)
        frames(100, open = 0.95f)
        assertLogged("blink: short blink 528 ms")
        assertLogged("scan: short blink, now on \"I am in pain\"")
    }

    @Test
    fun `a fast blink logs why it was ignored`() {
        frames(100, open = 0.95f)
        frames(99, open = 0.05f)
        frames(100, open = 0.95f)
        assertLogged("blink: ignored 99 ms, shorter than 500 ms")
    }

    @Test
    fun `a long closure logs why it was ignored`() {
        frames(100, open = 0.95f)
        frames(2_100, open = 0.05f)
        frames(100, open = 0.95f)
        assertTrue(lines.any { it.startsWith("blink: ignored") && it.endsWith("longer than 1900 ms") })
    }

    @Test
    fun `losing the face logs the reason`() {
        frames(100, open = 0.95f)
        frames(600, open = 0.95f, yaw = 40f)
        assertLogged("blink: face lost: head turned (yaw 40, pitch 0)")
        frames(100, open = 0.95f)
        frames(600, open = 0.95f, faceFound = false)
        assertLogged("blink: face lost: no face")
    }

    @Test
    fun `taps and board changes are logged`() {
        controller.onTap(com.outspoken.conversation.Board.YES_NO, time)
        assertLogged("scan: tap on Yes / No")
        assertLogged("scan: cards now [Yes, No]")
    }
}
