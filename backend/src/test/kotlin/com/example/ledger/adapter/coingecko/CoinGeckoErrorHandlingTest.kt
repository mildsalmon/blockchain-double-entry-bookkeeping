package com.example.ledger.adapter.coingecko

import com.example.ledger.adapter.common.RateLimiter
import com.example.ledger.adapter.common.RetryExecutor
import com.example.ledger.domain.model.PriceInfo
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.port.PriceCacheRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoinGeckoErrorHandlingTest {

    private val coinGeckoClient = mockk<CoinGeckoClient>()
    private val tokenIdMapper = mockk<TokenIdMapper>()
    private val priceCacheRepository = mockk<PriceCacheRepository>(relaxed = true)
    private val rateLimiter = mockk<RateLimiter>(relaxed = true)

    private val fromDate: LocalDate = LocalDate.of(2024, 1, 1)
    private val toDate: LocalDate = LocalDate.of(2024, 1, 1)

    private fun makeAdapter(retryExecutor: RetryExecutor) = CoinGeckoAdapter(
        coinGeckoClient = coinGeckoClient,
        tokenIdMapper = tokenIdMapper,
        priceCacheRepository = priceCacheRepository,
        coinGeckoRateLimiter = rateLimiter,
        retryExecutor = retryExecutor
    )

    @Test
    fun `429 response is retried and succeeds on subsequent attempt`() {
        val retryExecutor = RetryExecutor(maxAttempts = 3, initialDelayMillis = 1, multiplier = 1.0)
        val adapter = makeAdapter(retryExecutor)

        every { tokenIdMapper.resolve(any(), any()) } returns "ethereum"
        every { priceCacheRepository.find(any(), any(), any()) } returns null

        val ex429 = WebClientResponseException.create(429, "Too Many Requests", org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null, null)
        val successResult = mapOf(fromDate to BigDecimal("3000.00"))
        val savedInfo = PriceInfo(
            tokenAddress = null,
            tokenSymbol = "ETH",
            date = fromDate,
            priceKrw = BigDecimal("3000.00"),
            source = PriceSource.COINGECKO
        )

        var callCount = 0
        every { coinGeckoClient.fetchRangePrices(any(), any(), any()) } answers {
            callCount += 1
            if (callCount < 3) throw ex429
            successResult
        }
        every { priceCacheRepository.save(any()) } returnsArgument 0

        val result = adapter.getPricesInRange(null, "ETH", fromDate, toDate)

        assertEquals(3, callCount, "Expected 3 attempts (2 failures + 1 success)")
        assertTrue(result.isNotEmpty(), "Expected non-empty result after retry success")
        assertEquals(PriceSource.COINGECKO, result[fromDate]?.source)
    }

    @Test
    fun `404 response returns emptyMap without crashing`() {
        val retryExecutor = RetryExecutor(maxAttempts = 3, initialDelayMillis = 1, multiplier = 1.0)
        val adapter = makeAdapter(retryExecutor)

        every { tokenIdMapper.resolve(any(), any()) } returns "unknown-token-id"
        every { priceCacheRepository.find(any(), any(), any()) } returns null

        val ex404 = WebClientResponseException.create(404, "Not Found", org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null, null)
        every { coinGeckoClient.fetchRangePrices(any(), any(), any()) } throws ex404

        val result = adapter.getPricesInRange(null, "UNKNOWN", fromDate, toDate)

        assertTrue(result.isEmpty(), "Expected emptyMap for 404 response")
    }

    @Test
    fun `500 response returns emptyMap after retries exhausted`() {
        val retryExecutor = RetryExecutor(maxAttempts = 3, initialDelayMillis = 1, multiplier = 1.0)
        val adapter = makeAdapter(retryExecutor)

        every { tokenIdMapper.resolve(any(), any()) } returns "ethereum"
        every { priceCacheRepository.find(any(), any(), any()) } returns null

        val ex500 = WebClientResponseException.create(500, "Internal Server Error", org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null, null)
        var callCount = 0
        every { coinGeckoClient.fetchRangePrices(any(), any(), any()) } answers {
            callCount += 1
            throw ex500
        }

        val result = adapter.getPricesInRange(null, "ETH", fromDate, toDate)

        assertEquals(3, callCount, "Expected 3 attempts before giving up")
        assertTrue(result.isEmpty(), "Expected emptyMap after 500 retries exhausted")
    }

    @Test
    fun `rateLimiter acquire is called before retryExecutor block`() {
        val retryExecutor = RetryExecutor(maxAttempts = 1, initialDelayMillis = 1, multiplier = 1.0)
        val adapter = makeAdapter(retryExecutor)

        every { tokenIdMapper.resolve(any(), any()) } returns "ethereum"
        every { priceCacheRepository.find(any(), any(), any()) } returns null
        every { coinGeckoClient.fetchRangePrices(any(), any(), any()) } returns emptyMap()

        adapter.getPricesInRange(null, "ETH", fromDate, toDate)

        verify(exactly = 1) { rateLimiter.acquire() }
    }
}
