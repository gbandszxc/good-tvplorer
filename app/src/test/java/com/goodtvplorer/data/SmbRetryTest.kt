package com.goodtvplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

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
}
