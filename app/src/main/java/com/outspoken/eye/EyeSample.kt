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
    val dots: FaceDots? = null,
)

/** A point in the upright camera image. */
data class Dot(val x: Float, val y: Float)

/**
 * Eye outlines for the debug view, only filled while the dots are switched on. Points are scaled
 * to 0..1 of the upright image and not mirrored. [leftShape] and [rightShape] are [eyeShape]
 * values, measured in pixels before scaling.
 */
data class FaceDots(
    val leftEye: List<Dot>,
    val rightEye: List<Dot>,
    val imageAspect: Float,
    val leftShape: Float?,
    val rightShape: Float?,
)
