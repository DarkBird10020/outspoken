package com.outspoken.suggest

import com.outspoken.log.EventLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelStartTest {

    private val lines = mutableListOf<String>()
    private val log = EventLog { area, message -> lines += "$area: $message" }

    @Test
    fun `the drafter on the GPU is tried first`() {
        assertEquals(StartPlan("GPU", speculative = true), START_PLANS.first())
        assertEquals(listOf("GPU with MTP", "GPU", "CPU"), START_PLANS.map { it.label })
    }

    @Test
    fun `uses the drafter when it starts`() {
        val (plan, engine) = startFirst(START_PLANS, log) { it.label }
        assertEquals("GPU with MTP", plan.label)
        assertEquals("GPU with MTP", engine)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `a model without a drafter still runs on the GPU`() {
        val (plan, _) = startFirst(START_PLANS, log) {
            if (it.speculative) throw IllegalStateException("no drafter in this model")
            it.label
        }
        assertEquals("GPU", plan.label)
        assertEquals(listOf("model: could not start on GPU with MTP: no drafter in this model"), lines)
    }

    @Test
    fun `falls back to the CPU when the GPU fails`() {
        val (plan, _) = startFirst(START_PLANS, log) {
            if (it.backend == "GPU") throw RuntimeException("no GPU")
            it.label
        }
        assertEquals("CPU", plan.label)
        assertEquals(2, lines.size)
    }

    @Test
    fun `reports the last failure when nothing starts`() {
        try {
            startFirst(START_PLANS, log) { throw RuntimeException("broken file ${it.label}") }
            fail("expected a failure")
        } catch (e: RuntimeException) {
            assertEquals("broken file CPU", e.message)
        }
        assertEquals(3, lines.size)
    }
}
