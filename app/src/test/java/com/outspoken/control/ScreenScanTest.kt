package com.outspoken.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("Close the eyes to press it", scan.lit()?.action)
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
        // Menu, then three groups of up to five, shared evenly: four each.
        scan.next()
        // The group names its first buttons and says what a close does.
        assertEquals("Chat 0, Chat 1, Chat 2, …", scan.lit()?.label)
        assertEquals("Close the eyes to open these 4", scan.lit()?.action)
        assertEquals(Box(0, 100, 100, 480), scan.lit()?.box)
        assertEquals(4, scan.lit()?.parts?.size)
        assertEquals(2, scan.lit()?.step)
        assertEquals(4, scan.lit()?.of)
        scan.next()
        assertNull(scan.select())
        assertEquals("Chat 4", scan.lit()?.label)
        scan.next()
        assertEquals(ScanAction.Press(5), scan.select())
    }

    @Test
    fun `buttons are shared evenly between the groups, so none is left with one`() {
        val scan = ScreenScan(groupSize = 5)
        // The launcher in the 08:28 run: 36 buttons made seven groups of 5 and a last one of 1.
        scan.show((0 until 36).map { button(0, 100 + it * 100, "App $it") })
        assertEquals(9, scan.lit()?.of)
        scan.previous()
        assertEquals("Close the eyes to open these 4", scan.lit()?.action)
        scan.next()
        scan.next()
        assertEquals("Close the eyes to open these 5", scan.lit()?.action)
    }

    @Test
    fun `the log names where the scan is, never what the other app's buttons say`() {
        val scan = ScreenScan(groupSize = 5)
        scan.show((0 until 12).map { button(0, 100 + it * 100, "Chat with Meera $it") })
        val seen = mutableListOf(scan.where())
        scan.next()
        seen += scan.where()
        scan.select()
        seen += scan.where()
        scan.previous()
        seen += scan.where()
        scan.select()
        seen += scan.where()
        scan.select()
        seen += scan.where()
        assertEquals(
            listOf("Menu", "group 1 of 3, 4 buttons", "button 1 of 4 in group 1", "back to all groups", "Menu", "menu: Stop phone control"),
            seen,
        )
        assertFalse(seen.any { "Meera" in it })

        scan.show(listOf(button(0, 100, "Send"), button(0, 300, "Call")))
        scan.next()
        assertEquals("button 1 of 2", scan.where())
    }

    @Test
    fun `the card says how to move the way the eye settings do`() {
        assertEquals("look down: next  ·  look up: back", controlMoveHint(moveByEyes = true, upMovesNext = false))
        // Looking down off, or no look down line from calibration: a look up moves on (08:28 run).
        assertEquals("look up: next", controlMoveHint(moveByEyes = true, upMovesNext = true))
        assertEquals("it moves on by itself", controlMoveHint(moveByEyes = false, upMovesNext = true))
    }

    @Test
    fun `the last entry of a group goes back up`() {
        val scan = ScreenScan(groupSize = 2)
        scan.show((0 until 4).map { button(0, 100 + it * 100, "B$it") })
        scan.next()
        scan.select()
        scan.previous()
        assertEquals("Back to all groups", scan.lit()?.label)
        assertNull(scan.select())
        assertEquals("Menu", scan.lit()?.label)
    }

    @Test
    fun `the menu offers stop first, then back, home, scrolling and typing`() {
        val scan = ScreenScan()
        scan.show(listOf(button(0, 100, "A")))
        assertNull(scan.select())
        // Stop is the first thing lit in the menu (09:29 run: Back chosen ten times to get out).
        assertEquals("Stop phone control", scan.lit()?.label)
        assertEquals("Close the eyes to stop and go back to Outspoken", scan.lit()?.action)
        assertEquals(ScanAction.Menu(MenuAction.Stop), scan.select())
        scan.next()
        assertEquals("Back", scan.lit()?.label)
        assertEquals(ScanAction.Menu(MenuAction.Back), scan.select())
        repeat(4) { scan.next() }
        assertEquals("Type", scan.lit()?.label)
        scan.next()
        assertEquals("Back to the app", scan.lit()?.label)
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
        assertEquals("Stop phone control", scan.lit()?.label)
    }
}
