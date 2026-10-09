package com.outspoken.eye

/** Smoothed camera frame rate, worked out from frame timestamps. */
class FpsMeter(private val smoothing: Float = 0.1f) {

    var fps = 0f
        private set

    private var lastMs = 0L

    fun onFrame(timeMs: Long): Float {
        if (lastMs in 1 until timeMs) {
            val now = 1000f / (timeMs - lastMs)
            fps = if (fps == 0f) now else fps + (now - fps) * smoothing
        }
        lastMs = timeMs
        return fps
    }
}
