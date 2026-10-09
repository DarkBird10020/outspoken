package com.outspoken.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardTest {

    private val board = Board()

    @Test
    fun `first page leads with water`() {
        assertEquals("I need water", board.replies.first())
        assertEquals(4, board.replies.size)
        assertEquals(listOf(0, 1, 2, 3, Board.MORE_OPTIONS, Board.YES_NO), board.cards)
    }

    @Test
    fun `phrase bank covers the PRD list`() {
        val all = PhraseBank.phrases + PhraseBank.yesNo
        listOf("Yes", "No", "pain", "water", "toilet", "too hot", "too cold", "nurse", "Thank you").forEach { word ->
            assert(all.any { it.contains(word) }) { "missing $word" }
        }
    }

    @Test
    fun `choosing a reply returns its sentence`() {
        assertEquals("I need water", board.choose(0))
        assertEquals("Please call the nurse", board.choose(2))
    }

    @Test
    fun `more options pages through and wraps`() {
        assertNull(board.choose(Board.MORE_OPTIONS))
        assertEquals(listOf("I am too hot", "I am too cold", "Thank you"), board.replies)
        assertEquals(listOf(0, 1, 2, Board.MORE_OPTIONS, Board.YES_NO), board.cards)
        board.choose(Board.MORE_OPTIONS)
        assertEquals("I need water", board.replies.first())
    }

    @Test
    fun `yes no card shows yes and no`() {
        assertNull(board.choose(Board.YES_NO))
        assertEquals(listOf("Yes", "No"), board.replies)
        assertEquals("No", board.choose(1))
    }

    @Test
    fun `more options from yes no goes back to the first page`() {
        board.choose(Board.MORE_OPTIONS)
        board.choose(Board.YES_NO)
        board.choose(Board.MORE_OPTIONS)
        assertEquals("I need water", board.replies.first())
    }

    @Test
    fun `missing reply card says nothing`() {
        board.choose(Board.YES_NO)
        assertNull(board.choose(3))
    }

    @Test
    fun `suggestions come first and the phrase bank follows`() {
        val replies = listOf("My back hurts", "My head hurts", "It is getting worse", "I need medicine")
        board.choose(Board.MORE_OPTIONS)
        board.showSuggestions(replies)
        assertEquals(replies, board.replies)
        board.choose(Board.MORE_OPTIONS)
        assertEquals("I need water", board.replies.first())
        board.choose(Board.MORE_OPTIONS)
        board.choose(Board.MORE_OPTIONS)
        assertEquals(replies, board.replies)
    }

    @Test
    fun `clearing suggestions goes back to the phrase bank`() {
        board.showSuggestions(listOf("A", "B", "C", "D"))
        board.showSuggestions(null)
        assertEquals("I need water", board.replies.first())
    }

    @Test
    fun `frequent phrases in session are prioritized on earlier pages`() {
        val testBoard = Board()
        testBoard.updateFrequent(listOf("Thank you"))
        assertEquals("Thank you", testBoard.replies.first())
    }
}
