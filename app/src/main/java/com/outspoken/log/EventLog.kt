package com.outspoken.log

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

    override fun write(area: String, message: String) = sink.write(area, message)
}
