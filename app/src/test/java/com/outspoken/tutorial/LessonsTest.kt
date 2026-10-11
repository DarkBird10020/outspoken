package com.outspoken.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsTest {

    private val lessons = Lessons.all()

    private fun text(lesson: Lesson) = lesson.steps.joinToString(" ") { "${it.title} ${it.body}" }

    private fun lesson(id: String, controls: EyeControls = EyeControls()) = Lessons.all(controls).first { it.id == id }

    @Test
    fun `every lesson has a unique id and at least three steps with words`() {
        assertEquals(lessons.size, lessons.map { it.id }.toSet().size)
        lessons.forEach { lesson ->
            assertTrue(lesson.id, lesson.steps.size >= 3)
            assertTrue(lesson.id, lesson.title.isNotBlank() && lesson.summary.isNotBlank())
            lesson.steps.forEach { assertTrue(lesson.id, it.title.isNotBlank() && it.body.isNotBlank()) }
        }
    }

    @Test
    fun `the saved ids of finished lessons stay the same`() {
        assertEquals(
            listOf(
                "welcome", "setup", "blink", "move", "main-page", "say-anything", "help", "hand-signs", "listening",
                "language", "profile", "sos", "eye-check", "tuning", "model", "stats", "logs",
            ),
            lessons.map { it.id },
        )
    }

    @Test
    fun `each practice belongs to the lesson the app finishes with it`() {
        assertEquals(Practice.Blink, lesson("blink").practice)
        assertEquals(Practice.Move, lesson("move").practice)
        assertEquals(Practice.Help, lesson("help").practice)
        assertEquals(3, lessons.count { it.practice != null })
    }

    @Test
    fun `the lessons start with those for everyone and the speaker's come before the carer's`() {
        assertEquals(Audience.Everyone, lessons.first().audience)
        val firstCarer = lessons.indexOfFirst { it.audience == Audience.Carer }
        assertTrue(lessons.drop(firstCarer).none { it.audience == Audience.Speaker })
    }

    @Test
    fun `moving is taught with looks up by default`() {
        val move = text(lesson("move"))
        assertTrue(move.contains("Look up, towards the top edge of the phone"))
        assertFalse(move.contains("wink"))
    }

    @Test
    fun `moving is taught with looks down and up when looking down is on`() {
        val move = text(lesson("move", EyeControls(lookDown = true)))
        assertTrue(move.contains("Look down, past the bottom of the screen"))
    }

    @Test
    fun `winks are taught only when they are switched on`() {
        assertTrue(text(lesson("move", EyeControls(winks = true))).contains("a left wink moves to the next card"))
    }

    @Test
    fun `blink only is taught as the timed scan at its speed`() {
        val move = lesson("move", EyeControls(moveByEyes = false, scanMs = 2_000))
        assertEquals("Blink only", move.steps.first().title)
        assertTrue(text(move).contains("one card every 2.0 s"))
        assertEquals(Practice.Move, move.practice)
    }

    @Test
    fun `the hand signs lesson says whether they are on`() {
        assertTrue(text(lesson("hand-signs")).contains("off at the moment"))
        assertTrue(text(lesson("hand-signs", EyeControls(handSigns = true))).contains("Hand signs are switched on"))
    }

    @Test
    fun `continue opens the first lesson not yet finished`() {
        assertEquals("welcome", Lessons.nextUnfinished(lessons, emptySet())?.id)
        assertEquals("blink", Lessons.nextUnfinished(lessons, setOf("welcome", "setup", "move"))?.id)
        assertNull(Lessons.nextUnfinished(lessons, lessons.map { it.id }.toSet()))
    }
}
