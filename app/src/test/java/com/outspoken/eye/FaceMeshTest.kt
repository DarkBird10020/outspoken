package com.outspoken.eye

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceMeshTest {

    @Test
    fun `nose halfway between the face sides is facing the camera`() {
        assertEquals(0f, FaceMesh.headTurnDeg(0.5f, 0.3f, 0.7f), 0.01f)
    }

    @Test
    fun `nose off centre is a head turn`() {
        assertEquals(30f, kotlin.math.abs(FaceMesh.headTurnDeg(0.6f, 0.3f, 0.7f)), 0.5f)
        assertEquals(90f, kotlin.math.abs(FaceMesh.headTurnDeg(0.7f, 0.3f, 0.7f)), 0.5f)
    }

    @Test
    fun `looking to their right moves the gaze to the right of the screen`() {
        val scores = mapOf("eyeLookOutRight" to 0.8f, "eyeLookInLeft" to 0.6f)
        val gaze = FaceMesh.gaze { scores[it] ?: 0f }
        assertEquals(0.7f, gaze.x, 0.001f)
        assertEquals(0f, gaze.y, 0.001f)
    }

    @Test
    fun `looking up and down moves the gaze vertically`() {
        val up = FaceMesh.gaze { if (it.startsWith("eyeLookUp")) 0.5f else 0f }
        val down = FaceMesh.gaze { if (it.startsWith("eyeLookDown")) 0.4f else 0f }
        assertEquals(-0.5f, up.y, 0.001f)
        assertEquals(0.4f, down.y, 0.001f)
    }

    @Test
    fun `each eye outline has sixteen points and the irises ten`() {
        assertEquals(16, FaceMesh.LEFT_EYE.size)
        assertEquals(16, FaceMesh.RIGHT_EYE.size)
        assertEquals(10, (FaceMesh.IRIS_CENTRES + FaceMesh.IRIS_RIMS).distinct().size)
    }

    @Test
    fun `iris below the corner line reads as looking down`() {
        assertEquals(0.1f, FaceMesh.irisDrop(Dot(0f, 0f), Dot(10f, 0f), Dot(5f, 1f))!!, 0.0001f)
        assertEquals(-0.05f, FaceMesh.irisDrop(Dot(10f, 0f), Dot(0f, 0f), Dot(5f, -0.5f))!!, 0.0001f)
    }

    @Test
    fun `a head tilt alone is not an iris drop`() {
        // Corners tilted 45 degrees with the iris on the corner line.
        assertEquals(0f, FaceMesh.irisDrop(Dot(0f, 0f), Dot(10f, 10f), Dot(5f, 5f))!!, 0.0001f)
    }

    @Test
    fun `both eyes are averaged whichever iris number belongs to which eye`() {
        val points = mapOf(
            33 to Dot(0f, 0f), 133 to Dot(10f, 0f), 263 to Dot(30f, 0f), 362 to Dot(20f, 0f),
            468 to Dot(25f, 2f), 473 to Dot(5f, 1f),
        )
        assertEquals(0.15f, FaceMesh.irisDrop { points.getValue(it) }!!, 0.0001f)
    }
}
