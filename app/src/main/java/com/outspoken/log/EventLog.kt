package com.outspoken.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A sink for short lines about what the app saw and did. [area] groups related lines. */
fun interface EventLog {
    fun write(area: String, message: String)

    companion object {
        val None = EventLog { _, _ -> }
    }
}

/** The app-wide log. Writes nowhere until the app sets [sink] at start-up. */
object AppLog : EventLog {
    @Volatile
    var sink: EventLog = EventLog.None

    private val _recent = MutableStateFlow<List<String>>(emptyList())

    /** The last few blink, look and selection lines, for the debug screen. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    override fun write(area: String, message: String) {
        sink.write(area, message)
        if (onScreen(area, message)) _recent.update { (it + "$area: $message").takeLast(RECENT_LINES) }
    }

    private fun onScreen(area: String, message: String) =
        area == "blink" || area == "gaze" || (area == "scan" && !message.startsWith("highlight"))

    private const val RECENT_LINES = 8
}
