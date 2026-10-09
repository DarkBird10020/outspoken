package com.outspoken.eye

/**
 * Eye state from one camera frame. Open values run from 0 (shut) to 1 (open).
 * Angles are head rotation in degrees, 0 when facing the camera.
 */
data class EyeSample(
    val timeMs: Long,
    val faceFound: Boolean,
    val leftOpen: Float? = null,
    val rightOpen: Float? = null,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
)
