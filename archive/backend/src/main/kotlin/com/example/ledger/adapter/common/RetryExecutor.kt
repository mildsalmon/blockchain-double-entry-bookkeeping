package com.example.ledger.adapter.common

class RetryExecutor(
    private val maxAttempts: Int = 3,
    private val initialDelayMillis: Long = 100,
    private val multiplier: Double = 2.0
) {
    fun <T> execute(operation: () -> T): T {
        require(maxAttempts > 0) { "maxAttempts must be greater than 0" }
        var attempt = 0
        var delayMillis = initialDelayMillis
        var lastError: Throwable? = null

        while (attempt < maxAttempts) {
            attempt += 1
            try {
                return operation()
            } catch (error: Throwable) {
                lastError = error
                if (attempt >= maxAttempts) {
                    break
                }
                Thread.sleep(delayMillis)
                delayMillis = (delayMillis * multiplier).toLong().coerceAtLeast(1L)
            }
        }

        throw lastError ?: IllegalStateException("Retry failed without captured exception")
    }
}
