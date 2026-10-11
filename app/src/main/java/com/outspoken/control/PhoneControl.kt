package com.outspoken.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.toArgb
import com.outspoken.help.SosAccessibility
import com.outspoken.log.AppLog
import com.outspoken.ui.theme.EyesFoundDot
import com.outspoken.ui.theme.Ink

/**
 * Phone control (owner request): another app's buttons, lit one at a time over that app, chosen
 * by eye ([ScreenScan]). The accessibility helper reads the buttons and presses them; the log
 * gets the app's name and how many buttons it has, never what they say.
 */
object PhoneControl {

    private val main = Handler(Looper.getMainLooper())
    private val scan = ScreenScan()
    private var nodes: List<AccessibilityNodeInfo> = emptyList()
    private var shownKey: List<Box> = emptyList()
    private var overlay: Overlay? = null
    private var onStop: (() -> Unit)? = null
    private var onType: (() -> Unit)? = null
    private val refresh = Runnable { readScreen() }

    /** When the last eye frame came, to tell the person when the camera has stopped. */
    @Volatile
    private var lastEyesMs = 0L
    private var eyesLost = false
    private val eyesWatch = object : Runnable {
        override fun run() {
            if (!on) return
            val lost = typing.not() && android.os.SystemClock.elapsedRealtime() - lastEyesMs > EYES_LOST_MS
            if (lost != eyesLost) {
                eyesLost = lost
                AppLog.write("control", if (lost) "no eye frames for ${EYES_LOST_MS / 1000} s: the camera is not running" else "eye frames again")
                draw()
            }
            main.postDelayed(this, EYES_CHECK_MS)
        }
    }

    /** An eye frame reached phone control. */
    fun eyesRead() {
        lastEyesMs = android.os.SystemClock.elapsedRealtime()
    }

    @Volatile
    var on = false
        private set

    /** Outspoken is in front writing a sentence for the app below; the eyes go to Outspoken. */
    @Volatile
    var typing = false
        private set

    /**
     * Starts reading the screen and lighting its buttons; false when the accessibility helper is
     * off. [onStop] runs when control ends from its menu; [onType] when "Type" is chosen.
     */
    fun start(onStop: () -> Unit, onType: () -> Unit, goHome: Boolean = true): Boolean {
        val service = SosAccessibility.running ?: return false
        this.onStop = onStop
        this.onType = onType
        on = true
        overlay = Overlay(service).also { it.show() }
        AppLog.write("control", "phone control on${if (goHome) ", home screen" else ", in the app opened"}")
        if (goHome) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        lastEyesMs = android.os.SystemClock.elapsedRealtime()
        eyesLost = false
        main.postDelayed(eyesWatch, EYES_CHECK_MS)
        screenChanged()
        return true
    }

    fun stop(reason: String) {
        if (!on) return
        on = false
        typing = false
        main.removeCallbacks(refresh)
        main.removeCallbacks(eyesWatch)
        overlay?.hide()
        overlay = null
        nodes = emptyList()
        shownKey = emptyList()
        AppLog.write("control", "phone control off: $reason")
        onStop?.invoke()
    }

    /** Something on screen changed; read it again once it settles. */
    fun screenChanged() {
        if (!on || typing) return
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, SETTLE_MS)
    }

    fun next() = moved { scan.next() }

    fun previous() = moved { scan.previous() }

    fun select() {
        if (!on) return
        when (val action = scan.select()) {
            null -> draw()
            is ScanAction.Press -> press(action.target)
            is ScanAction.Menu -> menu(action.action)
        }
    }

    private fun moved(step: () -> Unit) {
        if (!on) return
        step()
        draw()
    }

    private fun draw() {
        val lit = scan.lit()
        val shown = if (eyesLost) {
            (lit ?: Lit(null, "")).copy(label = "Eyes not read", action = "The camera has stopped: open Outspoken")
        } else {
            lit
        }
        overlay?.light(shown)
    }

    private fun readScreen() {
        if (!on || typing) return
        val service = SosAccessibility.running ?: return stop("the accessibility helper went off")
        val found = service.screenButtons() ?: return
        nodes = found.map { it.first }
        val key = found.map { it.second.box }
        scan.refresh(found.map { it.second })
        if (key != shownKey) {
            shownKey = key
            AppLog.write("control", "${service.rootInActiveWindow?.packageName}: ${found.size} buttons")
        }
        draw()
    }

    private fun press(target: Int) {
        val service = SosAccessibility.running ?: return
        val node = nodes.getOrNull(target) ?: return
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        val pressed = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true || tap(service, node)
        AppLog.write("control", if (pressed) "pressed a button" else "could not press the button")
        screenChanged()
    }

    /** A tap at the middle of [node], for a button that does not take a click. */
    private fun tap(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also(node::getBoundsInScreen)
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS)).build()
        return service.dispatchGesture(gesture, null, null)
    }

    private fun menu(action: MenuAction) {
        val service = SosAccessibility.running ?: return
        AppLog.write("control", "menu: ${action.label}")
        when (action) {
            MenuAction.Back -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            MenuAction.Home -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            MenuAction.ScrollDown -> scroll(service, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            MenuAction.ScrollUp -> scroll(service, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            MenuAction.Type -> onType?.invoke()
            MenuAction.Outspoken -> {
                backToOutspoken(service)
                stop("back to Outspoken")
                return
            }
        }
        screenChanged()
    }

    /** Scrolls the largest scrolling part of the screen, a list or a page. */
    private fun scroll(service: AccessibilityService, action: Int) {
        val root = service.rootInActiveWindow ?: return
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH) return
            if (node.isScrollable && node.isVisibleToUser) {
                val r = Rect().also(node::getBoundsInScreen)
                val area = r.width() * r.height()
                if (area > bestArea) {
                    best = node
                    bestArea = area
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        val scrolled = best?.performAction(action) == true
        AppLog.write("control", if (scrolled) "scrolled" else "nothing to scroll here")
    }

    /**
     * "Type": when the app in front has a text box, Outspoken comes forward to build the sentence;
     * false when there is nowhere to type.
     */
    fun startTyping(): Boolean {
        val service = SosAccessibility.running ?: return false
        val root = service.rootInActiveWindow ?: return false
        if (textBox(root) == null) {
            AppLog.write("control", "type: no text box on this screen")
            return false
        }
        typing = true
        main.removeCallbacks(refresh)
        overlay?.hide()
        AppLog.write("control", "type: writing a sentence for ${root.packageName}")
        backToOutspoken(service)
        return true
    }

    /**
     * The sentence is written (or [text] is null when it was left): the app below is back in
     * front, and the sentence goes into its text box, where the eyes can then press Send.
     */
    fun finishTyping(text: String?) {
        if (!typing) return
        typing = false
        main.postDelayed({
            if (!on) return@postDelayed
            overlay?.show()
            if (text != null) fill(text)
            screenChanged()
        }, RETURN_MS)
    }

    /** The text box being typed in, else the first one on screen. */
    private fun textBox(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        fun walk(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (depth > MAX_DEPTH) return null
            if (node.isEditable && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> walk(child, depth + 1)?.let { return it } }
            return null
        }
        return walk(root, 0)
    }

    private fun fill(text: String) {
        val root = SosAccessibility.running?.rootInActiveWindow ?: return
        val box = textBox(root)
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val done = box?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
        AppLog.write("control", if (done) "type: ${text.length} characters put in the text box" else "type: the text box did not take the sentence")
    }

    /** Brings Outspoken back in front; from the helper, which Android lets start it. */
    fun backToOutspoken(context: Context? = SosAccessibility.running) {
        val from = context ?: return
        from.packageManager.getLaunchIntentForPackage(from.packageName)?.let {
            from.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    /**
     * The highlight over other apps: a green box round the lit button, and a label at the foot
     * of the screen with what is lit and how to move. It keeps the screen on, as Outspoken does.
     * Not in the design (a design gap): the theme's green and ink.
     */
    private class Overlay(private val service: AccessibilityService) {
        private val windows = service.getSystemService(WindowManager::class.java)
        private val view = HighlightView(service)

        fun show() {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
            runCatching { windows.addView(view, params) }.onFailure { AppLog.write("control", "overlay not shown: $it") }
        }

        fun hide() {
            runCatching { windows.removeView(view) }
        }

        fun light(lit: Lit?) {
            view.lit = lit
            view.invalidate()
        }
    }

    private class HighlightView(context: Context) : View(context) {
        var lit: Lit? = null
        private val density = context.resources.displayMetrics.density
        private val green = EyesFoundDot.toArgb()
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6 * density
            color = green
        }
        private val part = Paint(box).apply { strokeWidth = 2 * density }
        private val dim = Paint().apply { color = 0x66000000 }
        private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ink.toArgb()
            textSize = 22 * density
            isFakeBoldText = true
        }
        private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Ink.toArgb()
            textSize = 17 * density
        }
        private val small = Paint(body).apply { textSize = 14 * density }

        /**
         * Everything but the lit part is dimmed, so it is plain what is lit; a group's buttons are
         * outlined inside it. A card says what is lit, what a close does, and where it is in the
         * list. The menu entries have no place on screen; their card sits in the middle.
         */
        override fun onDraw(canvas: Canvas) {
            val now = lit ?: return
            val location = IntArray(2).also(::getLocationOnScreen)
            fun local(b: Box) = RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()).apply {
                offset(-location[0].toFloat(), -location[1].toFloat())
            }
            val litRect = now.box?.let { local(it).apply { inset(-6 * density, -6 * density) } }
            val dimmed = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
                litRect?.let { addRoundRect(it, 14 * density, 14 * density, Path.Direction.CW) }
            }
            canvas.drawPath(dimmed, dim)
            now.parts.forEach { canvas.drawRoundRect(local(it), 8 * density, 8 * density, part) }
            litRect?.let { canvas.drawRoundRect(it, 14 * density, 14 * density, box) }

            val margin = 20 * density
            val textWidth = width - 4 * margin
            val lines = listOf(
                title to now.label.take(MAX_LABEL),
                body to now.action,
                small to listOfNotNull(
                    "${now.step} of ${now.of}".takeIf { now.of > 0 },
                    "look down: next",
                    "look up: back",
                ).joinToString("  ·  "),
            ).filter { it.second.isNotEmpty() }
            val wrapped = lines.flatMap { (paint, text) -> wrap(text, paint, textWidth).map { paint to it } }
            val lineGap = 8 * density
            val cardHeight = wrapped.sumOf { (paint, _) -> (paint.textSize + lineGap).toDouble() }.toFloat() + 2 * margin
            // The card goes in the middle for the menu, else at whichever end the lit part is not.
            val top = when {
                litRect == null -> (height - cardHeight) / 2
                litRect.centerY() > height / 2 -> 56 * density
                else -> height - cardHeight - 56 * density
            }
            val area = RectF(margin, top, width - margin, top + cardHeight)
            canvas.drawRoundRect(area, 24 * density, 24 * density, card)
            canvas.drawRoundRect(area, 24 * density, 24 * density, box)
            var y = area.top + margin
            wrapped.forEach { (paint, text) ->
                y += paint.textSize
                canvas.drawText(text, area.left + margin, y, paint)
                y += lineGap
            }
        }

        private fun wrap(text: String, paint: Paint, width: Float): List<String> {
            val out = mutableListOf<String>()
            var line = ""
            for (word in text.split(' ')) {
                val tried = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(tried) <= width || line.isEmpty()) line = tried else {
                    out += line
                    line = word
                }
            }
            if (line.isNotEmpty()) out += line
            return out
        }
    }

    private const val SETTLE_MS = 400L
    private const val EYES_CHECK_MS = 1_000L
    private const val EYES_LOST_MS = 3_000L

    /** Time for the app below to come back in front before the sentence goes in. */
    private const val RETURN_MS = 700L
    private const val TAP_MS = 60L
    private const val MAX_DEPTH = 40
    private const val MAX_LABEL = 40
}
