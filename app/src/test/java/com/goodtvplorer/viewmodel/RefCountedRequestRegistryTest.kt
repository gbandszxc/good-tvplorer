package com.goodtvplorer.viewmodel

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class RefCountedRequestRegistryTest {
    @Test
    fun `same key starts once and cancel all stops active request`() = runBlocking {
        val started = AtomicInteger()
        val cancelled = CompletableDeferred<Unit>()
        val requests = RefCountedRequestRegistry(this)
        val block: suspend () -> Unit = {
            started.incrementAndGet()
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }

        requests.request("photo", block)
        requests.request("photo", block)
        yield()
        requests.release("photo")
        yield()

        assertEquals(1, started.get())
        assertFalse(cancelled.isCompleted)

        requests.cancelAll()
        withTimeout(1_000) { cancelled.await() }
    }
}
