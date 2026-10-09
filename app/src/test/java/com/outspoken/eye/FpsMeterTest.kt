package com.outspoken.eye

import org.junit.Assert.assertEquals
import org.junit.Test

class FpsMeterTest {

    @Test
    fun `first frame has no rate yet`() {
        assertEquals(0f, FpsMeter().onFrame(1_000), 0f)
    }

    @Test
    fun `steady frames give the true rate`() {
        val meter = FpsMeter()
        listOf(1_000L, 1_050L, 1_100L, 1_150L).forEach(meter::onFrame)
        assertEquals(20f, meter.fps, 0.001f)
    }

    @Test
    fun `one slow frame only nudges the rate`() {
        val meter = FpsMeter(smoothing = 0.1f)
        meter.onFrame(1_000)
        meter.onFrame(1_050)
        assertEquals(19f, meter.onFrame(1_150), 0.001f)
    }

    @Test
    fun `repeated or backwards timestamps are ignored`() {
        val meter = FpsMeter()
        meter.onFrame(1_000)
        meter.onFrame(1_050)
        meter.onFrame(1_050)
        meter.onFrame(900)
        assertEquals(20f, meter.fps, 0.001f)
    }
}
