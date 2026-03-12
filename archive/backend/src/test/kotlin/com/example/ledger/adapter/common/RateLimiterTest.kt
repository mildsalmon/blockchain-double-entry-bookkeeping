package com.example.ledger.adapter.common

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class RateLimiterTest {

    @Test
    fun `waits when limit is exceeded`() {
        val limiter = RateLimiter(maxCalls = 1, periodMillis = 80)

        limiter.acquire()
        val elapsed = measureTimeMillis {
            limiter.acquire()
        }

        assertTrue(elapsed >= 70, "expected elapsed >= 70ms, got ${'$'}elapsed")
    }
}
