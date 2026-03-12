package com.example.ledger.adapter.common

import java.util.ArrayDeque

class RateLimiter(
    private val maxCalls: Int,
    private val periodMillis: Long
) {
    private val callTimestamps = ArrayDeque<Long>()

    @Synchronized
    fun acquire() {
        val now = System.currentTimeMillis()
        evictExpired(now)

        if (callTimestamps.size >= maxCalls) {
            val oldest = callTimestamps.first()
            val waitMillis = (oldest + periodMillis) - now
            if (waitMillis > 0) {
                Thread.sleep(waitMillis)
            }
            evictExpired(System.currentTimeMillis())
        }

        callTimestamps.addLast(System.currentTimeMillis())
    }

    private fun evictExpired(currentTimeMillis: Long) {
        while (callTimestamps.isNotEmpty()) {
            val oldest = callTimestamps.first()
            if (currentTimeMillis - oldest >= periodMillis) {
                callTimestamps.removeFirst()
            } else {
                break
            }
        }
    }
}
