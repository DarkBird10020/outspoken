package com.outspoken.control

/**
 * Tells when a press by eye opened another app: a different app in front within [withinMs] of
 * the press. On the phone the apps came 1.1 s after the tap (Contacts 09:40:48.573 after a tap at
 * 09:40:47.424, File Manager 09:41:11.255 after 09:41:10.096). Used to end the tips once the
 * person has opened an app, and to end phone control there when that is switched on.
 */
class OpenedApp(private val withinMs: Long = OPEN_WITHIN_MS) {

    private var from: String? = null
    private var atMs = 0L

    /** A button was pressed while [app] was in front. */
    fun pressed(app: String?, nowMs: Long) {
        from = app
        atMs = nowMs
    }

    /** [app] is in front now; true once when it is another app than the one pressed in. */
    fun onScreen(app: String?, nowMs: Long): Boolean {
        val pressedIn = from ?: return false
        if (nowMs - atMs > withinMs) {
            from = null
            return false
        }
        if (app == null || app == pressedIn) return false
        from = null
        return true
    }

    private companion object {
        const val OPEN_WITHIN_MS = 3_000L
    }
}
