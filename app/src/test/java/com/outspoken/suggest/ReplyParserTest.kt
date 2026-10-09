package com.outspoken.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyParserTest {

    private val four = listOf("Yes, my back hurts", "A little", "No pain now", "Call the nurse")

    @Test
    fun `plain list is accepted`() {
        assertEquals(four, parseReplies("""["Yes, my back hurts", "A little", "No pain now", "Call the nurse"]"""))
    }

    @Test
    fun `code fence and chatter around the list are ignored`() {
        val output = "Sure! Here you go:\n```json\n[\"Yes, my back hurts\",\n \"A little\",\n \"No pain now\",\n \"Call the nurse\"]\n```"
        assertEquals(four, parseReplies(output))
    }

    @Test
    fun `trailing comma is tolerated`() {
        assertEquals(four, parseReplies("""["Yes, my back hurts","A little","No pain now","Call the nurse",]"""))
    }

    @Test
    fun `replies are trimmed and escapes decoded`() {
        assertEquals(
            listOf("I said \"no\"", "Back\\slash", "Café please", "Fine"),
            parseReplies("""[" I said \"no\" ", "Back\\slash", "Café please", "Fine"]"""),
        )
    }

    @Test
    fun `wrong count is rejected`() {
        assertNull(parseReplies("""["Yes", "No", "Maybe"]"""))
        assertNull(parseReplies("""["A", "B", "C", "D", "E"]"""))
    }

    @Test
    fun `long reply is rejected`() {
        assertNull(parseReplies("""["Yes", "No", "Maybe", "one two three four five six seven eight nine ten eleven"]"""))
    }

    @Test
    fun `empty or repeated replies are rejected`() {
        assertNull(parseReplies("""["Yes", "No", " ", "Maybe"]"""))
        assertNull(parseReplies("""["Yes", "No", "yes", "Maybe"]"""))
    }

    @Test
    fun `not a list of strings is rejected`() {
        assertNull(parseReplies("Yes, No, Maybe, Later"))
        assertNull(parseReplies("""[1, 2, 3, 4]"""))
        assertNull(parseReplies("""["Yes", "No", "Maybe", "Later""""))
        assertNull(parseReplies("""['Yes', 'No', 'Maybe', 'Later']"""))
    }

    @Test
    fun `string list parser handles empty lists`() {
        assertEquals(emptyList<String>(), parseStringList("[ ]"))
        assertNull(parseStringList("[\"a\"] trailing"))
    }

    @Test
    fun `lenient extraction keeps a list with the wrong count`() {
        assertEquals(listOf("Yes", "No"), extractReplies("""["Yes", "No"]"""))
    }

    @Test
    fun `lenient extraction pulls quoted strings out of chatter`() {
        assertEquals(listOf("My back hurts", "I need rest"), extractReplies("Sure! \"My back hurts\" or maybe \"I need rest\"."))
    }

    @Test
    fun `lenient extraction reads numbered and bulleted lines`() {
        assertEquals(listOf("Yes please", "Not now"), extractReplies("1. Yes please
2) Not now"))
        assertEquals(listOf("Water", "Juice"), extractReplies("- Water
* Juice"))
    }

    @Test
    fun `lenient extraction drops long and repeated replies`() {
        val long = (1..12).joinToString(" ") { "word" }
        assertEquals(listOf("Yes"), extractReplies("""["Yes", "yes", "$long"]"""))
    }

    @Test
    fun `nothing usable gives an empty list`() {
        assertEquals(emptyList<String>(), extractReplies("no"))
    }
}
