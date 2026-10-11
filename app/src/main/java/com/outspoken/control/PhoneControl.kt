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
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.outspoken.R
import com.outspoken.eye.EyeReader
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

    /** How to move on, shown on the card; set from the eye settings ([controlMoveHint]). */
    @Volatile
    var moveHint = controlMoveHint(moveByEyes = true, upMovesNext = false)

    /** A short word on the card after a choice that did nothing, for [NOTICE_MS]. */
    private var notice: String? = null
    private val noticeOff = Runnable {
        notice = null
        draw()
    }

    /** When the last eye frame came, to tell the person when the camera has stopped. */
    @Volatile
    private var lastEyesMs = 0L
    private var eyesLost = false
    private var cameraFramesAtCheck = 0L
    private val eyesWatch = object : Runnable {
        override fun run() {
            if (!on) return
            val cameraFrames = EyeReader.framesIn - cameraFramesAtCheck
            cameraFramesAtCheck = EyeReader.framesIn
            val lost = typing.not() && android.os.SystemClock.elapsedRealtime() - lastEyesMs > EYES_LOST_MS
            if (lost != eyesLost) {
                eyesLost = lost
                AppLog.write(
                    "control",
                    if (!lost) "eye frames again"
                    else if (cameraFrames == 0L) "no eye frames for ${EYES_LOST_MS / 1000} s: the camera sends no frames"
                    else "no eye frames for ${EYES_LOST_MS / 1000} s: the camera sent $cameraFrames frames in the last second but none reached phone control",
                )
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
        // A new start begins at the menu, not inside the group lit when control last stopped
        // (09:00:43: "pressed button 1 of 4 in group 6" right after control came back).
        scan.show(emptyList())
        overlay = Overlay(service) { stop("stopped by hand with the Stop button") }.also { it.show() }
        AppLog.write("control", "phone control on${if (goHome) ", home screen" else ", in the app opened"}")
        if (goHome) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        lastEyesMs = android.os.SystemClock.elapsedRealtime()
        eyesLost = false
        cameraFramesAtCheck = EyeReader.framesIn
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
        main.removeCallbacks(noticeOff)
        notice = null
        overlay?.hide()
        overlay = null
        nodes = emptyList()
        shownKey = emptyList()
        AppLog.write("control", "phone control off: $reason")
        onStop?.invoke()
    }

    /** Events from the app in front, counted to tell whether a press changed anything. */
    private var screenEvents = 0L

    /** Something on screen changed; read it again once it settles. */
    fun screenChanged() {
        screenEvents++
        if (!on || typing) return
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, SETTLE_MS)
    }

    fun next() = moved { scan.next() }

    fun previous() = moved { scan.previous() }

    fun select() {
        if (!on) return
        val chosen = scan.where()
        when (val action = scan.select()) {
            null -> {
                AppLog.write("control", "chose $chosen, now lit: ${scan.where()}")
                draw()
            }
            is ScanAction.Press -> press(action.target, chosen)
            is ScanAction.Menu -> menu(action.action)
        }
    }

    private fun moved(step: () -> Unit) {
        if (!on) return
        step()
        AppLog.write("control", "lit: ${scan.where()}")
        draw()
    }

    private fun draw() {
        val lit = scan.lit()
        val waiting = notice
        val shown = when {
            eyesLost -> (lit ?: Lit(null, "")).copy(label = "Eyes not read", action = "The camera has stopped: open Outspoken")
            waiting != null -> (lit ?: Lit(null, "")).copy(action = waiting)
            else -> lit
        }
        overlay?.light(shown?.copy(moves = moveHint))
    }

    /** Says on the card, for a few seconds, why a choice did nothing. */
    private fun tell(text: String) {
        notice = text
        main.removeCallbacks(noticeOff)
        main.postDelayed(noticeOff, NOTICE_MS)
        draw()
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

    /**
     * Presses the button with an accessibility click; when the screen has not changed
     * [CLICK_CHECK_MS] later, taps its middle the way a finger would. The launcher took eight
     * clicks in the 09:00 run ("pressed button 1 of 4 in group 6", 09:00:20 to 09:00:57) and no
     * app opened, as some screens accept a click and do nothing with it.
     */
    private fun press(target: Int, place: String) {
        val service = SosAccessibility.running ?: return
        val node = nodes.getOrNull(target) ?: return
        val what = "$place (${node.className?.toString()?.substringAfterLast('.') ?: "view"})"
        val app = service.rootInActiveWindow?.packageName
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        val clicked = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        screenChanged()
        if (!clicked) {
            AppLog.write("control", "$what took no click; tapping it")
            tap(node, what)
            return
        }
        AppLog.write("control", "pressed $what")
        val before = screenEvents
        main.postDelayed({
            // Only on the same app: a tap on a new screen would press whatever is there.
            val same = SosAccessibility.running?.rootInActiveWindow?.packageName == app
            if (on && !typing && screenEvents == before && same) {
                AppLog.write("control", "nothing changed ${CLICK_CHECK_MS} ms after the click; tapping it")
                tap(node, what)
            }
        }, CLICK_CHECK_MS)
    }

    /** A tap at the middle of [node], as a finger would; the log says whether Android did it. */
    private fun tap(node: AccessibilityNodeInfo, what: String) {
        val service = SosAccessibility.running ?: return
        node.refresh()
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.isEmpty) {
            AppLog.write("control", "could not tap $what: it is no longer on screen")
            tell("Could not press it")
            return
        }
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS)).build()
        val sent = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                AppLog.write("control", "tapped $what")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                AppLog.write("control", "the tap on $what was cancelled")
                tell("Could not press it")
            }
        }, null)
        if (!sent) {
            AppLog.write("control", "could not tap $what: Android refused the gesture")
            tell("Could not press it")
        }
        screenChanged()
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
        if (!scrolled) tell("Nothing to scroll here")
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
            tell("No text box here: open a chat first")
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
     * A Stop button in the top corner takes a touch, so a carer can end phone control by hand:
     * the owner could not find a way out and chose the menu's Home four times (08:50:49 to
     * 08:50:56), which keeps control on. Not in the design (a design gap): the theme's green and ink.
     */
    private class Overlay(private val service: AccessibilityService, private val onStopTapped: () -> Unit) {
        private val windows = service.getSystemService(WindowManager::class.java)
        private val view = HighlightView(service)
        private val density = service.resources.displayMetrics.density
        private val stopButton = TextView(service).apply {
            setText(R.string.control_stop)
            textSize = 16f
            setTextColor(Ink.toArgb())
            val padH = (16 * density).toInt()
            val padV = (10 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 20 * density
                setStroke((2 * density).toInt(), EyesFoundDot.toArgb())
            }
            setOnClickListener { onStopTapped() }
        }

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
            val stopParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = (12 * density).toInt()
                y = (STOP_TOP_DP * density).toInt()
            }
            runCatching { windows.addView(stopButton, stopParams) }
                .onSuccess { AppLog.write("control", "Stop button shown, top right") }
                .onFailure { AppLog.write("control", "Stop button not shown: $it") }
        }

        fun hide() {
            runCatching { windows.removeView(view) }
            runCatching { windows.removeView(stopButton) }
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
                    now.moves.ifEmpty { null },
                ).joinToString("  ·  "),
            ).filter { it.second.isNotEmpty() }
            val wrapped = lines.flatMap { (paint, text) -> wrap(text, paint, textWidth).map { paint to it } }
            val lineGap = 8 * density
            val cardHeight = wrapped.sumOf { (paint, _) -> (paint.textSize + lineGap).toDouble() }.toFloat() + 2 * margin
            // The card goes in the middle for the menu, else at whichever end the lit part is not.
            val top = when {
                litRect == null -> (height - cardHeight) / 2
                // Below the Stop button.
                litRect.centerY() > height / 2 -> CARD_TOP_DP * density
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
    private const val NOTICE_MS = 3_000L

    /** The Stop button's place from the top of the screen, and the card's top below it. */
    private const val STOP_TOP_DP = 40
    private const val CARD_TOP_DP = 100
    private const val EYES_CHECK_MS = 1_000L
    private const val EYES_LOST_MS = 3_000L

    /** Time for the app below to come back in front before the sentence goes in. */
    private const val RETURN_MS = 700L
    private const val TAP_MS = 60L

    /** How long a click has to change the screen before the button is tapped instead. */
    private const val CLICK_CHECK_MS = 800L
    private const val MAX_DEPTH = 40
    private const val MAX_LABEL = 40
}
