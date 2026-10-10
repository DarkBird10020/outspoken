package com.outspoken.suggest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

/**
 * Runs blocking [work] on [executor] and returns its result. If the caller is cancelled first,
 * [stop] is called so the work can end early, and this still waits for [work] to return before
 * passing the cancellation on, so the next caller never finds it running.
 */
suspend fun <T> runStoppable(executor: Executor, stop: () -> Unit, work: () -> T): T {
    val done = CompletableDeferred<Result<T>>()
    executor.execute { done.complete(runCatching(work)) }
    try {
        return done.await().getOrThrow()
    } catch (e: CancellationException) {
        runCatching(stop)
        throw e
    } finally {
        withContext(NonCancellable) { done.await() }
    }
}
