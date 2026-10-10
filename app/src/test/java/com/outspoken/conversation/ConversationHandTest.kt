package com.outspoken.conversation

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
}
