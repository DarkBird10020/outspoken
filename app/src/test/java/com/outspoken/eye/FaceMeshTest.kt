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
}
