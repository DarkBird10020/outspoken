package com.outspoken.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Another app's buttons chosen by eye: groups, then a button; the menu first. */
class ScreenScanTest {

    private fun button(left: Int, top: Int, label: String) = Target(Box(left, top, left + 100, top + 80), label)

    @Test
    fun `a few buttons are offered one by one after the menu`() {
        val scan = ScreenScan()
        scan.show(listOf(button(0, 500, "Send"), button(0, 100, "Back"), button(200, 100, "Call")))
        assertEquals("Menu", scan.lit()?.label)
        scan.next()
        // Reading order: the top row left to right, then the next row.
        assertEquals("Back", scan.lit()?.label)
        scan.next()
        assertEquals("Call", scan.lit()?.label)
        scan.next()
        assertEquals("Send", scan.lit()?.label)
        assertEquals(ScanAction.Press(0), scan.select())
    }

    @Test
    fun `looking up from the menu wraps to the last button`() {
        val scan = ScreenScan()
        scan.show(listOf(button(0, 100, "A"), button(0, 300, "B")))
        scan.previous()
        assertEquals("B", scan.lit()?.label)
    }

    @Test
    fun `many buttons are scanned as groups, then a button in the group`() {
        val scan = ScreenScan(groupSize = 5)
        val chats = (0 until 12).map { button(0, 100 + it * 100, "Chat $it") }
        scan.show(chats)
        // Menu, then three groups of up to five.
        scan.next()
        assertEquals("5 buttons", scan.lit()?.label)
        assertEquals(Box(0, 100, 100, 580), scan.lit()?.box)
        scan.next()
        assertNull(scan.select())
        assertEquals("Chat 5", scan.lit()?.label)
        scan.next()
        assertEquals(ScanAction.Press(6), scan.select())
    }

    @Test
    fun `the last entry of a group goes back up`() {
        val scan = ScreenScan(groupSize = 2)
        scan.show((0 until 4).map { button(0, 100 + it * 100, "B$it") })
        scan.next()
        scan.select()
        scan.previous()
        assertEquals("Up", scan.lit()?.label)
        assertNull(scan.select())
        assertEquals("Menu", scan.lit()?.label)
    }

    @Test
    fun `the menu offers back, home, scrolling, typing and Outspoken`() {
        val scan = ScreenScan()
        scan.show(listOf(button(0, 100, "A")))
        assertNull(scan.select())
        assertEquals("Back", scan.lit()?.label)
        assertEquals(ScanAction.Menu(MenuAction.Back), scan.select())
        repeat(5) { scan.next() }
        assertEquals("Back to Outspoken", scan.lit()?.label)
        scan.next()
        assertEquals("Up", scan.lit()?.label)
    }

    @Test
    fun `a new screen starts the scan again at the menu`() {
        val scan = ScreenScan()
        scan.show(listOf(button(0, 100, "A"), button(0, 200, "B")))
        scan.next()
        scan.show(listOf(button(0, 100, "C")))
        assertEquals("Menu", scan.lit()?.label)
    }

    @Test
    fun `the same buttons with new words keep the scan where it is`() {
        val scan = ScreenScan()
        scan.show(listOf(button(0, 100, "Chat, online"), button(0, 200, "Call")))
        scan.next()
        scan.next()
        // "online" became "typing…": same place, same buttons.
        scan.refresh(listOf(button(0, 100, "Chat, typing…"), button(0, 200, "Call")))
        assertEquals("Call", scan.lit()?.label)
        // A new screen starts again.
        scan.refresh(listOf(button(0, 300, "Send")))
        assertEquals("Menu", scan.lit()?.label)
    }

    @Test
    fun `an empty screen still offers the menu`() {
        val scan = ScreenScan()
        scan.show(emptyList())
        scan.next()
        assertEquals("Menu", scan.lit()?.label)
        assertNull(scan.select())
        assertEquals("Back", scan.lit()?.label)
    }
}
