package com.outspoken.conversation

import com.outspoken.eye.Dot
import com.outspoken.eye.EyeSample
import com.outspoken.hand.HandAction
import com.outspoken.scan.Scanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand signs in the conversation: a phrase said at once, or the lit card chosen by a fist. */
class ConversationHandTest {

    private val said = mutableListOf<String>()
    private val controller = ConversationController(speak = { said += it }, scanner = Scanner(intervalMs = 1_200))
    private var time = 10_000L

    @Test
    fun `a phrase sign says it and it joins the conversation`() {
        controller.onTick(time)
        controller.onHandSign(HandAction.Say("Yes"), time)
        assertEquals(listOf("Yes"), said)
        assertEquals(listOf("Yes"), controller.history)
    }

    @Test
    fun `with Hindi cards a phrase sign says it in Hindi`() {
        // 16:41:50 to 16:42:10 on the phone the signs said "Yes", "No", "Please wait" and "I love
        // you" in English while the cards were in Hindi.
        controller.language = AppLanguage.Hindi
        controller.onTick(time)
        controller.onHandSign(HandAction.Say("Please wait"), time)
        assertEquals(listOf("कृपया रुकिए"), said)
    }

    @Test
    fun `signs are ignored while the phone speaks`() {
        controller.onTick(time)
        controller.onHandSign(HandAction.Say("Yes"), time)
        controller.onHandSign(HandAction.Say("No"), time + 500)
        controller.onHandSign(HandAction.ChooseLit, time + 600)
        assertEquals(listOf("Yes"), said)
    }

    @Test
    fun `a fist chooses the lit card`() {
        controller.onTick(time)
        controller.onHandSign(HandAction.ChooseLit, time + 100)
        assertEquals(listOf("I need water"), said)
    }

    @Test
    fun `in Say anything a phrase sign waits but a fist still chooses`() {
        controller.onTick(time)
        controller.onTap(Board.SAY_ANYTHING, time)
        controller.onHandSign(HandAction.Say("Yes"), time + 100)
        assertTrue(said.isEmpty())
        controller.onHandSign(HandAction.ChooseLit, time + 200)
        assertEquals("I", controller.ui.value.builder!!.sentence)
    }

    @Test
    fun `by eyes a finger count lights that card and a fist says it`() {
        controller.moveByEyes = true
        controller.onTick(time)
        controller.onHandSign(HandAction.Light(2), time + 100)
        assertEquals(2, controller.ui.value.highlighted)
        assertTrue(said.isEmpty())
        controller.onHandSign(HandAction.ChooseLit, time + 1_200)
        assertEquals(listOf("Please call the nurse"), said)
    }

    @Test
    fun `in timed scanning a finger count leaves the highlight to the timer`() {
        controller.onTick(time)
        controller.onHandSign(HandAction.Light(2), time + 100)
        assertEquals(0, controller.ui.value.highlighted)
    }

    @Test
    fun `by eyes a hand in view stops looks moving the highlight off the card it lit`() {
        // Phone, 15:55:08 to 15:55:10: a hand lit card 4, a look down moved to More options, and
        // the fist chose More options.
        controller.moveByEyes = true
        controller.upMovesNext = true
        fun frames(ms: Long, gazeY: Float = 0f, hand: Boolean = false) {
            val end = time + ms
            while (time < end) {
                if (hand) controller.onHandInView(time)
                controller.onSample(EyeSample(time, true, 0.95f, 0.95f, gaze = Dot(0f, gazeY)))
                time += 33
            }
        }
        frames(500)
        controller.onHandSign(HandAction.Light(2), time)
        frames(500, gazeY = -0.7f, hand = true)
        frames(1_300, hand = true)
        assertEquals(2, controller.ui.value.highlighted)
        // Once the hand has gone, the same look moves the highlight again.
        frames(1_100)
        frames(500, gazeY = -0.7f)
        frames(1_300)
        assertEquals(3, controller.ui.value.highlighted)
    }
}
