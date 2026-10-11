package com.outspoken.help

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.outspoken.control.Box
import com.outspoken.control.PhoneControl
import com.outspoken.control.Target
import com.outspoken.log.AppLog

/**
 * The accessibility helper, for what the person in bed cannot press:
 * - while an SOS goes out over WhatsApp, Send and then the voice call button;
 * - while phone control is on, the buttons of the app in front, chosen by eye ([PhoneControl]).
 * It reads screens only for these. The log gets view ids and counts, never what a screen says.
 */
class SosAccessibility : AccessibilityService() {

    override fun onServiceConnected() {
        running = this
        AppLog.write("help", "WhatsApp helper on (Accessibility)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (PhoneControl.on && event.packageName?.toString() != packageName) PhoneControl.screenChanged()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (running === this) running = null
        PhoneControl.stop("the accessibility helper went off")
        AppLog.write("help", "WhatsApp helper off")
        return super.onUnbind(intent)
    }

    /**
     * The buttons of the app in front, for phone control; null while there is no window to read.
     * None while Outspoken itself is in front. A button covering most of the screen is a
     * background, not something to choose.
     */
    fun screenButtons(): List<Pair<AccessibilityNodeInfo, Target>>? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() == packageName) return emptyList()
        val screen = resources.displayMetrics
        val screenArea = screen.widthPixels.toLong() * screen.heightPixels
        val found = mutableListOf<Pair<AccessibilityNodeInfo, Target>>()
        val seen = mutableSetOf<Rect>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH) return
            if (node.isClickable && node.isEnabled && node.isVisibleToUser) {
                val r = Rect().also(node::getBoundsInScreen)
                val area = r.width().toLong() * r.height()
                if (!r.isEmpty && area < screenArea * MAX_SCREEN_SHARE && seen.add(r)) {
                    found += node to Target(Box(r.left, r.top, r.right, r.bottom), label(node))
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found
    }

    /** What a button says: its description, its text, the first text inside it, else its id. */
    private fun label(node: AccessibilityNodeInfo): String {
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        fun inner(n: AccessibilityNodeInfo, depth: Int): String? {
            if (depth > 4) return null
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                (child.contentDescription ?: child.text)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
                inner(child, depth + 1)?.let { return it }
            }
            return null
        }
        return inner(node, 0) ?: node.viewIdResourceName?.substringAfter(":id/")?.replace('_', ' ') ?: "Button"
    }

    /** The clickable buttons on the active window, when it is WhatsApp's. */
    fun whatsAppButtons(): List<Pair<AccessibilityNodeInfo, ScreenButton>>? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() !in WHATSAPP_PACKAGES) return null
        val found = mutableListOf<Pair<AccessibilityNodeInfo, ScreenButton>>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH) return
            if (node.isClickable && node.isEnabled && node.isVisibleToUser) {
                val label = node.contentDescription?.toString() ?: node.text?.toString()
                found += node to ScreenButton(node.viewIdResourceName, label)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found
    }

    companion object {
        /** The connected helper; null while it is off in Accessibility settings. */
        @Volatile
        var running: SosAccessibility? = null
            private set

        private const val MAX_DEPTH = 40

        /** A clickable part this big is the page behind the buttons. */
        private const val MAX_SCREEN_SHARE = 0.8
    }
}

/**
 * Presses WhatsApp's buttons for one SOS, in order, checking the screen every [POLL_MS] while
 * WhatsApp opens. Each step has [STEP_WAIT_MS] to show up; when it does not, the log lists the
 * button ids that were on screen, so the next fix can target the right one.
 */
object WhatsAppRun {

    private val handler = Handler(Looper.getMainLooper())
    private val steps = ArrayDeque<WhatsAppStep>()
    private var who = ""
    private var stepSinceMs = 0L
    private var lastSeen: List<String> = emptyList()
    private val poll = Runnable { check() }

    /** Starts pressing [plan] once WhatsApp is on screen; false when the helper is off. */
    fun start(plan: List<WhatsAppStep>, who: String): Boolean {
        handler.removeCallbacks(poll)
        steps.clear()
        if (SosAccessibility.running == null || plan.isEmpty()) return false
        steps.addAll(plan)
        this.who = who
        stepSinceMs = SystemClock.elapsedRealtime()
        lastSeen = emptyList()
        handler.postDelayed(poll, POLL_MS)
        return true
    }

    private fun check() {
        val step = steps.firstOrNull() ?: return
        val service = SosAccessibility.running ?: run {
            AppLog.write("help", "WhatsApp SOS to $who stopped: the helper went off before ${step.what}")
            steps.clear()
            return
        }
        val buttons = service.whatsAppButtons()
        if (buttons != null) {
            lastSeen = buttons.mapNotNull { it.second.id?.substringAfter(":id/") }.distinct()
            buttonFor(step, buttons.map { it.second })?.let { i ->
                val pressed = buttons[i].first.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                AppLog.write("help", "WhatsApp SOS to $who: ${if (pressed) "pressed" else "could not press"} ${step.what} (${buttons[i].second.id ?: "no id"})")
                if (pressed) {
                    next()
                    // Gives WhatsApp time to send the message before the call button.
                    if (steps.isNotEmpty()) handler.postDelayed(poll, AFTER_PRESS_MS)
                    return
                }
            }
        }
        val waited = SystemClock.elapsedRealtime() - stepSinceMs
        val wait = if (step == WhatsAppStep.ConfirmCall) CONFIRM_WAIT_MS else STEP_WAIT_MS
        if (waited >= wait) {
            if (step == WhatsAppStep.ConfirmCall) {
                AppLog.write("help", "WhatsApp SOS to $who: no \"Start voice call?\" question in ${waited / 1_000} s; buttons on screen: ${lastSeen.joinToString()}")
                next()
            } else {
                // The buttons last seen in WhatsApp, also when it has left the screen: at 07:22:01 the
                // call button was missed with WhatsApp gone, and the log could not say what it showed.
                val seen = "buttons last seen in WhatsApp: ${lastSeen.joinToString().ifEmpty { "none" }}"
                val screen = if (buttons == null) "WhatsApp not on screen now; $seen" else seen
                AppLog.write("help", "WhatsApp SOS to $who: ${step.what} not found in ${waited / 1_000} s; $screen")
                steps.clear()
            }
            if (steps.isEmpty()) return
        }
        handler.postDelayed(poll, POLL_MS)
    }

    private fun next() {
        steps.removeFirstOrNull()
        stepSinceMs = SystemClock.elapsedRealtime()
        if (steps.isEmpty()) AppLog.write("help", "WhatsApp SOS to $who: done")
    }

    private const val POLL_MS = 500L
    private const val AFTER_PRESS_MS = 1_500L
    private const val STEP_WAIT_MS = 15_000L
    private const val CONFIRM_WAIT_MS = 4_000L
}
