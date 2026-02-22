package com.example.ledger.adapter.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RetryConfig {

    @Bean
    fun retryExecutor(
        @Value("\${app.retry.max-attempts:3}") maxAttempts: Int,
        @Value("\${app.retry.initial-delay-millis:150}") initialDelayMillis: Long,
        @Value("\${app.retry.multiplier:2.0}") multiplier: Double
    ): RetryExecutor {
        return RetryExecutor(
            maxAttempts = maxAttempts,
            initialDelayMillis = initialDelayMillis,
            multiplier = multiplier
        )
    }

    @Bean
    fun coinGeckoRateLimiter(
        @Value("\${app.coingecko.rate-limit-per-minute:30}") maxCallsPerMinute: Int
    ): RateLimiter {
        return RateLimiter(maxCallsPerMinute, 60_000)
    }
}
