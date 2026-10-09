package com.outspoken.eye

import kotlin.math.asin
import kotlin.math.PI

/**
 * Point numbers in MediaPipe's 478-point face mesh, and the maths that turns mesh points and
 * blendshape scores into what the app needs. Pure so it can be tested.
 */
object FaceMesh {
    /** Outline of each eye, in order around the lids. */
    val RIGHT_EYE = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
    val LEFT_EYE = listOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466)

    /** Iris centre and four rim points of each eye (the last ten mesh points). */
    val IRIS_CENTRES = listOf(468, 473)
    val IRIS_RIMS = listOf(469, 470, 471, 472, 474, 475, 476, 477)

    const val NOSE_TIP = 1
    const val FACE_SIDE_A = 234
    const val FACE_SIDE_B = 454

    /**
     * Rough head turn in degrees from where the nose tip sits between the two sides of the face:
     * halfway is facing the camera, at one side is turned 90°. Only used to tell "facing the
     * phone" from "turned away", so the sign does not matter.
     */
    fun headTurnDeg(noseX: Float, sideAX: Float, sideBX: Float): Float {
        val span = sideBX - sideAX
        if (span == 0f) return 90f
        val offset = ((noseX - sideAX) / span - 0.5f) * 2
        return (asin(offset.coerceIn(-1f, 1f)) * 180 / PI).toFloat()
    }

    /**
     * Where the eyes look, from MediaPipe's eye blendshapes, as seen in the mirrored preview:
     * x from -1 (left of screen) to 1 (right), y from -1 (up) to 1 (down). Blendshape names
     * use the person's own left and right.
     */
    fun gaze(score: (String) -> Float): Dot {
        val towardTheirRight = (score("eyeLookOutRight") + score("eyeLookInLeft")) / 2
        val towardTheirLeft = (score("eyeLookOutLeft") + score("eyeLookInRight")) / 2
        val down = (score("eyeLookDownLeft") + score("eyeLookDownRight")) / 2
        val up = (score("eyeLookUpLeft") + score("eyeLookUpRight")) / 2
        return Dot(towardTheirRight - towardTheirLeft, down - up)
    }
}
