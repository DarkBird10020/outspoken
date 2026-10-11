package com.outspoken.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceCropTest {

    // The eye camera's upright picture.
    private val width = 480
    private val height = 640

    @Test
    fun `the face is in the middle of the photo, with room around it`() {
        val face = FaceBox(0.35f, 0.30f, 0.65f, 0.55f)
        val crop = faceCrop(width, height, face)
        assertEquals(PHOTO_FRAME_ASPECT, crop.width.toFloat() / crop.height, 0.01f)
        assertTrue(crop.left <= 0.35f * width && crop.left + crop.width >= 0.65f * width)
        assertTrue(crop.top <= 0.30f * height && crop.top + crop.height >= 0.55f * height)
        assertEquals((0.425f * height).toInt(), crop.top + crop.height / 2, 2)
    }

    @Test
    fun `a face near the top edge keeps the photo inside the picture`() {
        val crop = faceCrop(width, height, FaceBox(0.40f, 0.0f, 0.60f, 0.15f))
        assertEquals(0, crop.top)
        assertTrue(crop.left >= 0 && crop.left + crop.width <= width)
    }

    @Test
    fun `a face filling the picture gives the largest part that fits`() {
        val crop = faceCrop(width, height, FaceBox(0f, 0f, 1f, 1f))
        assertEquals(width, crop.width)
        assertEquals(640, crop.height)
    }

    @Test
    fun `with no face the middle is kept`() {
        val crop = faceCrop(width, 800, null)
        assertEquals(Crop(0, 80, 480, 640), crop)
    }
}
