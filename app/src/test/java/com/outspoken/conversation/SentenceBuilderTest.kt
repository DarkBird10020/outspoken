package com.outspoken.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceBuilderTest {

    private val builder = SentenceBuilder()

    @Test
    fun `an empty sentence shows built-in first words at once`() {
        assertEquals(listOf("I", "Please", "My", "Can you"), builder.shown)
        assertEquals("", builder.sentence)
        assertEquals(0, builder.wordCount)
    }

    @Test
    fun `an empty sentence has no Speak card and no Finish card`() {
        assertEquals(listOf(0, 1, 2, 3, SentenceBuilder.MORE_WORDS, SentenceBuilder.DELETE), builder.cards)
    }

    @Test
    fun `picked words build the sentence and the next words follow the last one`() {
        builder.add("I")
        builder.add("want")
        assertEquals("I want", builder.sentence)
        assertEquals(2, builder.wordCount)
        assertEquals(listOf("to", "some", "water", "my"), builder.shown)
        assertTrue(SentenceBuilder.SPEAK in builder.cards)
    }

    @Test
    fun `short phrases count every word`() {
        builder.add("I need")
        builder.add("the toilet")
        assertEquals(4, builder.wordCount)
        assertEquals("I need the toilet", builder.sentence)
    }

    @Test
    fun `the model's words come first and its finished sentence adds the Finish card`() {
        builder.add("I")
        builder.showModel(listOf("am", "want", "miss"), "I miss my family")
        assertEquals(listOf("am", "want", "miss", "need"), builder.shown)
        assertEquals("I miss my family", builder.completion)
        assertEquals(SentenceBuilder.FINISH, builder.cards.last())
    }

    @Test
    fun `a finished sentence the same as the sentence so far is not offered`() {
        builder.add("Thank you")
        builder.showModel(emptyList(), "thank you")
        assertNull(builder.completion)
    }

    @Test
    fun `adding a word clears the model's answer for the old sentence`() {
        builder.add("I")
        builder.showModel(listOf("miss"), "I miss my family")
        builder.add("miss")
        assertNull(builder.completion)
        assertFalse("miss" in builder.shown)
    }

    @Test
    fun `more words pages through and wraps round`() {
        val first = builder.shown
        builder.more()
        assertEquals(listOf("I need", "I want", "I feel", "Thank you"), builder.shown)
        builder.more()
        builder.more()
        assertEquals(first, builder.shown)
    }

    @Test
    fun `delete removes the last word and says false when nothing is left`() {
        builder.add("I")
        builder.add("want")
        assertTrue(builder.deleteLast())
        assertEquals("I", builder.sentence)
        assertTrue(builder.deleteLast())
        assertFalse(builder.deleteLast())
        assertTrue(builder.isEmpty)
    }
}
