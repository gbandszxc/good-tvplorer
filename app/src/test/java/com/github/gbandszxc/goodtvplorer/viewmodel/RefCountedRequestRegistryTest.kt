package com.github.gbandszxc.goodtvplorer.viewmodel

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class RefCountedRequestRegistryTest {
    @Test
    fun `cancel all except keeps selected request releasable`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = RefCountedRequestRegistry(scope)
        val selectedCancelled = CompletableDeferred<Unit>()
        val otherCancelled = CompletableDeferred<Unit>()

        requests.request("selected") {
            try {
                awaitCancellation()
            } finally {
                selectedCancelled.complete(Unit)
            }
        }
        requests.request("other") {
            try {
                awaitCancellation()
            } finally {
                otherCancelled.complete(Unit)
            }
        }

        requests.cancelAllExcept("selected")

        assertFalse(selectedCancelled.isCompleted)
        assertEquals(true, otherCancelled.isCompleted)
        requests.release("selected")
        assertEquals(true, selectedCancelled.isCompleted)
        scope.cancel()
    }

    @Test
    fun `cancel all tolerates requests removing themselves during cancellation`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = RefCountedRequestRegistry(scope)

        requests.request("first") { awaitCancellation() }
        requests.request("second") { awaitCancellation() }

        requests.cancelAll()
        scope.cancel()
    }

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
