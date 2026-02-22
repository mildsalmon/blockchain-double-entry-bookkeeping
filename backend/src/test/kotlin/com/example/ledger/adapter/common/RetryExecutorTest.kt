package com.example.ledger.adapter.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryExecutorTest {

    @Test
    fun `retries operation and throws after max attempts`() {
        val retryExecutor = RetryExecutor(maxAttempts = 3, initialDelayMillis = 1, multiplier = 1.0)
        var calls = 0

        assertFailsWith<IllegalStateException> {
            retryExecutor.execute {
                calls += 1
                throw IllegalStateException("always fail")
            }
        }

        assertEquals(3, calls)
    }

    @Test
    fun `returns result when eventual attempt succeeds`() {
        val retryExecutor = RetryExecutor(maxAttempts = 3, initialDelayMillis = 1, multiplier = 1.0)
        var calls = 0

        val result = retryExecutor.execute {
            calls += 1
            if (calls < 3) throw IllegalStateException("fail")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, calls)
    }
}
