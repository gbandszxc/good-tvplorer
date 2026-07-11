package com.goodtvplorer.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmbRetryTest {
    @Test
    fun `negotiated SMB reads are capped at one MiB for pipelining`() {
        assertEquals(1024 * 1024, smbReadBufferSize(8 * 1024 * 1024))
    }

    @Test
    fun `fresh connection is reused without probe`() {
        var now = 0L
        val verifier = IdleResourceVerifier(clockNanos = { now }, idleNanos = 5_000_000_000L)

        assertTrue(verifier.isUsable(connected = true))
    }

    @Test
    fun `idle connection is replaced without touching stale socket`() {
        var now = 0L
        val verifier = IdleResourceVerifier(clockNanos = { now }, idleNanos = 5_000_000_000L)

        now = 6_000_000_000L

        assertFalse(verifier.isUsable(connected = true))
    }

    @Test
    fun `successful activity refreshes idle lifetime`() {
        var now = 0L
        val verifier = IdleResourceVerifier(clockNanos = { now }, idleNanos = 5_000_000_000L)

        now = 4_000_000_000L
        verifier.markActive()
        now = 8_000_000_000L
        assertTrue(verifier.isUsable(connected = true))
        now = 10_000_000_000L
        assertFalse(verifier.isUsable(connected = true))
    }

    @Test
    fun `idle resource reconnects after active lease releases`() {
        var now = 0L
        var created = 0
        val closed = mutableListOf<Int>()
        data class Resource(val id: Int, val verifier: IdleResourceVerifier)
        val resources = ReusableResource(
            factory = {
                val id = ++created
                Resource(id, IdleResourceVerifier({ now }, 5_000_000_000L))
            },
            usable = { it.verifier.isUsable(connected = true) },
            close = { closed += it.id },
        )
        val active = resources.acquire().value
        active.verifier.retain()

        now = 6_000_000_000L
        val reused = resources.acquire()

        assertEquals(1, reused.value.id)
        assertEquals(emptyList(), closed)

        active.verifier.release()
        val replacement = resources.acquire()

        assertEquals(2, replacement.value.id)
        assertEquals(listOf(1), closed)
    }

    @Test
    fun `chunked copy checks cancellation between reads`() {
        var checks = 0
        val output = ByteArrayOutputStream()

        assertFailsWith<CancellationException> {
            copyStreamCancellable(
                input = ByteArrayInputStream(ByteArray(8)),
                output = output,
                bufferSize = 4,
                ensureActive = {
                    if (++checks == 2) throw CancellationException("cancelled")
                },
            )
        }

        assertEquals(4, output.size())
    }

    @Test
    fun `timeout invalidates failed generation before retry acquires replacement`() {
        var created = 0
        val closed = mutableListOf<Int>()
        val generations = mutableListOf<Long>()
        val resources = ReusableResource(
            factory = { ++created },
            usable = { true },
            close = closed::add,
        )

        val result = useResourceWithRetryOnce(resources, retryable = { it is SocketTimeoutException }) { lease ->
            generations += lease.generation
            if (generations.size == 1) throw SocketTimeoutException("timed out")
            lease.value
        }

        assertEquals(2, result)
        assertEquals(listOf(1L, 2L), generations)
        assertEquals(listOf(1), closed)
    }

    @Test
    fun `connection operation retries only once`() {
        var attempts = 0

        assertFails {
            retryConnectionOnce(retryable = { true }) {
                attempts++
                error("offline")
            }
        }

        assertEquals(2, attempts)
    }

    @Test
    fun `non retryable operation is not repeated`() {
        var attempts = 0

        assertFails {
            retryConnectionOnce(retryable = { false }) {
                attempts++
                error("denied")
            }
        }

        assertEquals(1, attempts)
    }

    @Test
    fun `SMB cancellation is propagated unchanged`() {
        assertFailsWith<CancellationException> {
            throwSmbFailure(CancellationException("left screen"))
        }
    }

    @Test
    fun `short SMB reads continue until requested range is full`() {
        var calls = 0

        val bytes = readRangeFully(maxBytes = 100, chunkSize = 64) { buffer, start, requested ->
            calls++
            val count = minOf(7, requested)
            repeat(count) { buffer[start + it] = 1 }
            count
        }

        assertEquals(100, bytes.size)
        assertTrue(calls > 2)
    }

    @Test
    fun `permanently closed source cannot reconnect`() {
        val source = SmbFileSource(
            SmbConnectionInfo("id", "test", "unreachable.invalid", share = "share", username = "", password = ""),
        )
        source.close()

        val error = assertFailsWith<IllegalStateException> { runBlocking { source.list("") } }

        assertTrue(error.message.orEmpty().contains("已关闭"))
    }
}
