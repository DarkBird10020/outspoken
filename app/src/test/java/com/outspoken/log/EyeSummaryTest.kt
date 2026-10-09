package com.outspoken.log

import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EyeSummaryTest {

    private val summary = EyeSummary(windowMs = 1_000)

    private fun face(timeMs: Long, open: Float) =
        EyeSample(timeMs, faceFound = true, leftOpen = open, rightOpen = open, yawDeg = 3.4f, pitchDeg = -2.2f)

    @Test
    fun `nothing is written inside the first window`() {
        for (t in 0L until 1_000L step 50) assertNull(summary.add(face(t, 0.9f)))
    }

    @Test
    fun `a new window writes the last one with the lowest eye values`() {
        for (t in 0L until 1_000L step 50) summary.add(face(t, if (t == 500L) 0.05f else 0.95f))
        assertEquals(
            "fps 20.0, face 20/20, left 0.95 min 0.05, right 0.95 min 0.05, yaw 3, pitch -2",
            summary.add(face(1_000, 0.95f)),
        )
    }

    @Test
    fun `no face leaves out the eye values`() {
        for (t in 0L until 1_000L step 100) summary.add(EyeSample(t, faceFound = false))
        assertEquals("fps 10.0, face 0/10", summary.add(EyeSample(1_000, faceFound = false)))
    }

    @Test
    fun `each window starts fresh`() {
        summary.add(face(0, 0.05f))
        summary.add(face(1_000, 0.95f))
        assertEquals(
            "fps 1.0, face 1/1, left 0.95 min 0.95, right 0.95 min 0.95, yaw 3, pitch -2",
            summary.add(face(2_000, 0.95f)),
        )
    }
}
