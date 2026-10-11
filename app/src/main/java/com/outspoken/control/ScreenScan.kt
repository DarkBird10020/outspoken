package com.outspoken.control

import kotlin.math.abs

/** A rectangle on the screen, in pixels. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY get() = (top + bottom) / 2
    val height get() = bottom - top

    companion object {
        /** The smallest box around all of [boxes]. */
        fun around(boxes: List<Box>) = Box(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
    }
}

/** Something on another app's screen that can be pressed: where it is and what it says. */
data class Target(val box: Box, val label: String)

/** What the menu of phone control offers, always reachable by eye. */
enum class MenuAction(val label: String, val does: String) {
    Back("Back", "go back one screen"),
    Home("Home", "go to the home screen"),
    ScrollDown("Scroll down", "show more below"),
    ScrollUp("Scroll up", "show more above"),
    Type("Type", "write a sentence into this app"),
    Outspoken("Back to Outspoken", "stop phone control"),
}

/** What a select does. */
sealed interface ScanAction {
    data class Press(val target: Int) : ScanAction
    data class Menu(val action: MenuAction) : ScanAction
}

/**
 * One thing lit by the scan, to draw: its box (null for the menu entries), what it is, what a
 * close does to it, where it is in the list ([step] of [of]) and, for a group, its buttons' boxes.
 * The owner could not tell what was being chosen from a box and "5 buttons" (08:10 run).
 * [moves] says how to move on, from the eye settings ([controlMoveHint]).
 */
data class Lit(
    val box: Box?,
    val label: String,
    val action: String = "",
    val step: Int = 0,
    val of: Int = 0,
    val parts: List<Box> = emptyList(),
    val moves: String = "",
)

/**
 * How the lit part moves on, for the card over the other app, as the main page's hint says it.
 * The card always read "look down: next · look up: back", also when a look down could not move
 * and a look up was the only way on (08:28 run).
 */
fun controlMoveHint(moveByEyes: Boolean, upMovesNext: Boolean): String = when {
    !moveByEyes -> "it moves on by itself"
    upMovesNext -> "look up: next"
    else -> "look down: next  ·  look up: back"
}

/**
 * Another app's buttons, chosen by eye the way the reply cards are: look down for the next, up
 * for the previous, a close to choose. A screen with many buttons is scanned in two steps, a group
 * of up to [groupSize] buttons in reading order and then a button in it, so a chat list of 25
 * takes about 8 looks instead of 25. The menu (Back, Home, scroll, Type, back to Outspoken) is the
 * first entry, so it is always one look away.
 */
class ScreenScan(private val groupSize: Int = GROUP_SIZE) {

    private var targets: List<Target> = emptyList()

    /** Indexes into [targets], in reading order. */
    private var order: List<Int> = emptyList()
    private var groups: List<List<Int>> = emptyList()

    private sealed interface Level {
        data object Top : Level
        data class Group(val index: Int) : Level
        data object Menu : Level
    }

    private var level: Level = Level.Top
    private var position = 0

    /** New buttons on screen: the scan starts again from the top. */
    fun show(screen: List<Target>) {
        targets = screen
        order = readingOrder(screen)
        groups = evenGroups(order, groupSize)
        level = Level.Top
        position = 0
    }

    /**
     * The same screen read again: when its buttons are where they were, only what they say is
     * updated and the scan stays where it is. A chat's "online" or a video's clock changing
     * would otherwise send the light back to the menu every moment.
     */
    fun refresh(screen: List<Target>) {
        if (screen.map { it.box } == targets.map { it.box }) targets = screen else show(screen)
    }

    fun next() = move(1)

    fun previous() = move(-1)

    /** Chooses what is lit: opens a group or the menu, or returns what to do. */
    fun select(): ScanAction? {
        when (val at = level) {
            Level.Top -> {
                val entries = topEntries()
                when (val entry = entries.getOrNull(position)) {
                    TopEntry.Menu -> enter(Level.Menu)
                    is TopEntry.Group -> enter(Level.Group(entry.index))
                    is TopEntry.Single -> return ScanAction.Press(entry.target)
                    null -> Unit
                }
            }
            is Level.Group -> {
                val group = groups[at.index]
                if (position == group.size) enter(Level.Top) else return ScanAction.Press(group[position])
            }
            Level.Menu -> {
                val actions = MenuAction.entries
                if (position == actions.size) enter(Level.Top) else return ScanAction.Menu(actions[position])
            }
        }
        return null
    }

    /**
     * Where the scan is, for the log: the menu's entries by name, the other app's buttons only by
     * their place, never by what they say. The 08:28 run could not show what a close had opened.
     */
    fun where(): String = when (val at = level) {
        Level.Top -> when (val entry = topEntries().getOrNull(position)) {
            TopEntry.Menu -> "Menu"
            is TopEntry.Group -> "group ${entry.index + 1} of ${groups.size}, ${groups[entry.index].size} buttons"
            is TopEntry.Single -> "button $position of ${order.size}"
            null -> "nothing"
        }
        is Level.Group -> {
            val group = groups[at.index]
            if (position == group.size) "back to all groups" else "button ${position + 1} of ${group.size} in group ${at.index + 1}"
        }
        Level.Menu -> MenuAction.entries.getOrNull(position)?.let { "menu: ${it.label}" } ?: "menu: back to the app"
    }

    /** What to draw now. */
    fun lit(): Lit? {
        val lit = when (val at = level) {
            Level.Top -> when (val entry = topEntries().getOrNull(position)) {
                TopEntry.Menu -> Lit(null, "Menu", "Close the eyes to open: Back, Home, Scroll, Type, Back to Outspoken")
                is TopEntry.Group -> {
                    val group = groups[entry.index]
                    val names = group.take(NAMES_SHOWN).joinToString(", ") { targets[it].label } + if (group.size > NAMES_SHOWN) ", …" else ""
                    Lit(Box.around(group.map { targets[it].box }), names, "Close the eyes to open these ${group.size}", parts = group.map { targets[it].box })
                }
                is TopEntry.Single -> Lit(targets[entry.target].box, targets[entry.target].label, PRESS)
                null -> null
            }
            is Level.Group -> {
                val group = groups[at.index]
                if (position == group.size) Lit(null, "Back to all groups", "Close the eyes to go back") else Lit(targets[group[position]].box, targets[group[position]].label, PRESS)
            }
            Level.Menu -> MenuAction.entries.getOrNull(position)?.let { Lit(null, it.label, "Close the eyes to ${it.does}") }
                ?: Lit(null, "Back to the app", "Close the eyes to leave the menu")
        }
        return lit?.copy(step = position + 1, of = size())
    }

    private sealed interface TopEntry {
        data object Menu : TopEntry
        data class Group(val index: Int) : TopEntry
        data class Single(val target: Int) : TopEntry
    }

    /** The menu, then the groups; a screen that fits one group shows its buttons straight away. */
    private fun topEntries(): List<TopEntry> =
        if (order.size <= groupSize) listOf(TopEntry.Menu) + order.map { TopEntry.Single(it) }
        else listOf(TopEntry.Menu) + groups.indices.map { TopEntry.Group(it) }

    private fun size(): Int = when (val at = level) {
        Level.Top -> topEntries().size
        // The last entry goes back up.
        is Level.Group -> groups[at.index].size + 1
        Level.Menu -> MenuAction.entries.size + 1
    }

    private fun move(by: Int) {
        val count = size()
        if (count == 0) return
        position = ((position + by) % count + count) % count
    }

    private fun enter(next: Level) {
        level = next
        position = 0
    }

    private companion object {
        const val GROUP_SIZE = 5

        /** A group's lit label names this many of its buttons. */
        const val NAMES_SHOWN = 3
        const val PRESS = "Close the eyes to press it"

        /**
         * [items] in groups of at most [size], as even as they can be: 36 buttons make four of 5
         * and four of 4, not seven of 5 and a last group of one, which took a close to open and
         * another to come back for a single button.
         */
        fun evenGroups(items: List<Int>, size: Int): List<List<Int>> {
            if (items.isEmpty()) return emptyList()
            val count = (items.size + size - 1) / size
            val base = items.size / count
            val extra = items.size % count
            var start = 0
            return List(count) { i ->
                val end = start + base + if (i < extra) 1 else 0
                items.subList(start, end).also { start = end }
            }
        }

        /**
         * Top to bottom, and left to right within a row. Buttons whose middles are within half
         * the smaller one's height of each other share a row.
         */
        fun readingOrder(targets: List<Target>): List<Int> {
            val byTop = targets.indices.sortedBy { targets[it].box.centerY }
            val rows = mutableListOf<MutableList<Int>>()
            for (i in byTop) {
                val row = rows.lastOrNull()
                val first = row?.firstOrNull()?.let { targets[it].box }
                val box = targets[i].box
                if (row != null && first != null && abs(first.centerY - box.centerY) <= minOf(first.height, box.height) / 2) {
                    row.add(i)
                } else {
                    rows.add(mutableListOf(i))
                }
            }
            return rows.flatMap { row -> row.sortedBy { targets[it].box.left } }
        }
    }
}
