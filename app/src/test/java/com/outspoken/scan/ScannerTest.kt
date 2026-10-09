package com.outspoken.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannerTest {

    private val cards = listOf(0, 1, 2, 3, 4, 5)

    @Test
    fun `highlight moves every interval and wraps`() {
        val scanner = Scanner(intervalMs = 1_200)
        scanner.restart(cards, nowMs = 10_000)
        assertEquals(0, scanner.cardAt(10_000))
        assertEquals(0, scanner.cardAt(11_199))
        assertEquals(1, scanner.cardAt(11_200))
        assertEquals(5, scanner.cardAt(10_000 + 5 * 1_200))
        assertEquals(0, scanner.cardAt(10_000 + 6 * 1_200))
    }

    @Test
    fun `paused time does not count`() {
        val scanner = Scanner(intervalMs = 1_000)
        scanner.restart(cards, nowMs = 0)
        scanner.pause(1_500)
        assertEquals(1, scanner.cardAt(9_000))
        scanner.resume(5_000)
        assertEquals(1, scanner.cardAt(5_400))
        assertEquals(2, scanner.cardAt(5_500))
    }

    @Test
    fun `restart while paused waits for resume`() {
        val scanner = Scanner(intervalMs = 1_000)
        scanner.pause(0)
        scanner.restart(cards, nowMs = 2_000)
        assertEquals(0, scanner.cardAt(4_000))
        scanner.resume(4_000)
        assertEquals(1, scanner.cardAt(5_000))
    }

    @Test
    fun `skips card ids that are not on screen`() {
        val scanner = Scanner(intervalMs = 1_000)
        scanner.restart(listOf(0, 1, 4, 5), nowMs = 0)
        assertEquals(4, scanner.cardAt(2_000))
        assertEquals(0, scanner.cardAt(4_000))
    }

    @Test
    fun `times before the last restart have no card`() {
        val scanner = Scanner(intervalMs = 1_000)
        scanner.restart(cards, nowMs = 5_000)
        assertNull(scanner.cardAt(4_999))
    }

    @Test
    fun `no cards means no highlight`() {
        assertNull(Scanner().cardAt(0))
    }
}
