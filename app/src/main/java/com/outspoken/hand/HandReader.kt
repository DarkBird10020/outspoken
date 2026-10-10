package com.outspoken.hand

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.outspoken.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera frames in, hand signs out, using MediaPipe's Gesture Recognizer (`gesture_recognizer.task`,
 * the same tasks-vision library as the face): its own label for the hand, and the fingers counted
 * from its 21 hand points ([fingersOut]) for counts it has no label for. It takes the eye reader's frames on the camera
 * thread and uses every second one: a sign is held for over half a second, and the face tracking
 * must keep its full rate. The model loads on the first frame after [enabled] is set, on the
 * camera thread, which is where it then runs. [onReading] runs on the main thread.
 */
class HandReader(context: Context, private val onReading: (HandReading, timeMs: Long) -> Unit) {

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var recognizer: GestureRecognizer? = null
    private var loadTried = false
    private var frameCount = 0

    @Volatile
    var enabled = false

    @Volatile
    private var closed = false

    private val _latest = MutableStateFlow(HandReading(null, 0f))
    val latest: StateFlow<HandReading> = _latest.asStateFlow()

    // Summary for the log, main thread only.
    private var frames = 0
    private var withHand = 0
    private var totalMs = 0L
    private val seen = mutableMapOf<String, Int>()
    private var lastSummaryMs = 0L

    /** Camera thread. */
    fun onFrame(upright: Bitmap, timeMs: Long) {
        if (!enabled || closed) return
        if (frameCount++ % EVERY_NTH_FRAME != 0) return
        if (recognizer == null && !loadTried) {
            loadTried = true
            recognizer = create()
        }
        val model = recognizer ?: return
        try {
            model.recognizeAsync(BitmapImageBuilder(upright).build(), timeMs)
        } catch (e: RuntimeException) {
            if (!closed) AppLog.write("hand", "frame not analysed: $e")
        }
    }

    /** Camera thread, after the last frame. */
    fun close() {
        closed = true
        recognizer?.close()
    }

    private fun create(): GestureRecognizer? {
        for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val options = GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(delegate).build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result: GestureRecognizerResult, input: MPImage -> onResult(result, input.width, input.height) }
                    .setErrorListener { error: RuntimeException -> AppLog.write("hand", "gesture recognizer error: $error") }
                    .build()
                return GestureRecognizer.createFromOptions(appContext, options).also {
                    AppLog.write("hand", "gesture recognizer ready on $delegate")
                }
            } catch (e: Exception) {
                AppLog.write("hand", "gesture recognizer could not start on $delegate: $e")
            }
        }
        return null
    }

    /** Runs on MediaPipe's thread. */
    private fun onResult(result: GestureRecognizerResult, width: Int, height: Int) {
        val timeMs = result.timestampMs()
        val handFound = result.gestures().isNotEmpty()
        val top = result.gestures().firstOrNull()?.maxByOrNull { it.score() }
        val label = top?.categoryName()
        val fingers = result.landmarks().firstOrNull()?.let { points ->
            fingersOut(points.map { HandPoint(it.x() * width, it.y() * height) })
        }
        val sign = HandSign.read(label, fingers)
        // A count is measured, not guessed by the model, so the model's score for "None" says
        // nothing about it.
        val score = if (label == NO_LABEL && sign != null) 1f else top?.score() ?: 0f
        val reading = HandReading(sign, score, fingers)
        // The model's label and the count side by side ("Open_Palm/4"), to see on the phone how it
        // reads four fingers with the thumb folded.
        val seenAs = if (handFound) "${label ?: "?"}/${fingers ?: "?"}" else "no hand"
        mainExecutor.execute { publish(reading, timeMs, handFound, seenAs) }
    }

    private fun publish(reading: HandReading, timeMs: Long, handFound: Boolean, label: String) {
        _latest.value = reading
        onReading(reading, timeMs)
        val now = SystemClock.elapsedRealtime()
        frames++
        if (handFound) withHand++
        totalMs += now - timeMs
        if (handFound) seen[label] = (seen[label] ?: 0) + 1
        if (now - lastSummaryMs >= SUMMARY_EVERY_MS) {
            val signs = if (seen.isEmpty()) "none" else seen.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }
            AppLog.write("hand", "$frames frames, a hand in $withHand, frame to result ${totalMs / frames} ms avg, read as: $signs")
            lastSummaryMs = now
            frames = 0
            withHand = 0
            totalMs = 0
            seen.clear()
        }
    }

    private companion object {
        const val MODEL = "gesture_recognizer.task"
        const val NO_LABEL = "None"
        const val EVERY_NTH_FRAME = 2
        const val SUMMARY_EVERY_MS = 5_000L
    }
}
