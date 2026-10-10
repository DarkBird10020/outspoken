package com.outspoken.suggest

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class StoppableTest {

    private val executor = Executors.newSingleThreadExecutor()

    @Test
    fun `returns what the work returns`() = runBlocking {
        assertEquals("four replies", runStoppable(executor, stop = {}) { "four replies" })
    }

    @Test
    fun `passes on what the work throws`() = runBlocking {
        try {
            runStoppable(executor, stop = {}) { error("model failed") }
            fail("expected the work's failure")
        } catch (e: IllegalStateException) {
            assertEquals("model failed", e.message)
        }
    }

    @Test
    fun `a cancelled caller stops the work and waits for it to return`() = runBlocking {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val order = mutableListOf<String>()
        val call = async {
            runStoppable(executor, stop = { order += "stop"; stopped.countDown() }) {
                started.countDown()
                // Like the native call: blocks until told to stop, then takes a moment to return.
                stopped.await(5, TimeUnit.SECONDS)
                Thread.sleep(50)
                synchronized(order) { order += "work returned" }
            }
        }
        while (!started.await(1, TimeUnit.MILLISECONDS)) yield()
        call.cancel()
        try {
            call.await()
            fail("expected the cancellation")
        } catch (e: CancellationException) {
            assertEquals(listOf("stop", "work returned"), synchronized(order) { order.toList() })
        }
        assertTrue(stopped.count == 0L)
    }
}
