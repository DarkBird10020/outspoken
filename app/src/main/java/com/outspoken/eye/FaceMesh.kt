package com.outspoken.eye

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.hypot

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

    /** The two corners of each eye. They stay put when the lids drop, unlike the lid points. */
    val EYE_CORNERS = listOf(33 to 133, 263 to 362)

    const val NOSE_TIP = 1
    const val FACE_SIDE_A = 234
    const val FACE_SIDE_B = 454

    /**
     * How far the iris centre sits below the line between the eye corners, as a share of the
     * eye's width; positive is down. Looking down moves the iris down between the corners even
     * when the upper lid drops with it, which on the phone made the "look down" blendshape read
     * like a close. Measured across the corner line, so a head tilt does not count.
     */
    fun irisDrop(cornerA: Dot, cornerB: Dot, iris: Dot): Float? {
        val width = hypot(cornerB.x - cornerA.x, cornerB.y - cornerA.y)
        if (width == 0f) return null
        val ux = (cornerB.x - cornerA.x) / width
        val uy = (cornerB.y - cornerA.y) / width
        // The side of the corner line that points down the image.
        val (nx, ny) = if (ux >= 0) -uy to ux else uy to -ux
        val dx = iris.x - (cornerA.x + cornerB.x) / 2
        val dy = iris.y - (cornerA.y + cornerB.y) / 2
        return (dx * nx + dy * ny) / width
    }

    /**
     * Average [irisDrop] of both eyes from mesh points given in pixels. Each eye takes the iris
     * centre nearest its corners, so which mesh number is which eye does not matter.
     */
    fun irisDrop(point: (Int) -> Dot): Float? {
        val irises = IRIS_CENTRES.map(point)
        val drops = EYE_CORNERS.mapNotNull { (a, b) ->
            val cornerA = point(a)
            val cornerB = point(b)
            val midX = (cornerA.x + cornerB.x) / 2
            val midY = (cornerA.y + cornerB.y) / 2
            val iris = irises.minByOrNull { hypot(it.x - midX, it.y - midY) } ?: return@mapNotNull null
            irisDrop(cornerA, cornerB, iris)
        }
        return if (drops.isEmpty()) null else drops.average().toFloat()
    }

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
