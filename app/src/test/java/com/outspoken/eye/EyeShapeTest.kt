package com.outspoken.eye

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class EyeShapeTest {

    /** 16 points around an ellipse, like ML Kit's eye outline. */
    private fun ellipse(width: Float, height: Float, cx: Float = 300f, cy: Float = 200f) =
        (0 until 16).map { i ->
            val t = 2 * PI * i / 16
            Dot(cx + width / 2 * cos(t).toFloat(), cy + height / 2 * sin(t).toFloat())
        }

    @Test
    fun `an open eye measures its height over width`() {
        assertEquals(0.35f, eyeShape(ellipse(40f, 14f))!!, 0.02f)
    }

    @Test
    fun `a shut eye measures close to zero`() {
        assertEquals(0.05f, eyeShape(ellipse(40f, 2f))!!, 0.01f)
    }

    @Test
    fun `size and position do not change the shape`() {
        assertEquals(eyeShape(ellipse(40f, 14f))!!, eyeShape(ellipse(80f, 28f, cx = 10f, cy = 900f))!!, 0.001f)
    }

    @Test
    fun `too few points cannot be measured`() {
        assertNull(eyeShape(listOf(Dot(0f, 0f), Dot(1f, 1f))))
        assertNull(eyeShape(List(16) { Dot(5f, 5f) }))
    }
}
