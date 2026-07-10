package com.goodtvplorer.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SmbRetryTest {
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
