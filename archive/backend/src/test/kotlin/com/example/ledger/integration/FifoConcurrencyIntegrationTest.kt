package com.example.ledger.integration

import com.example.ledger.domain.service.FifoService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FifoConcurrencyIntegrationTest : IntegrationTestBase() {

    private val walletAddress = "0xcccccccccccccccccccccccccccccccccccccccc"

    @Autowired
    private lateinit var fifoService: FifoService

    @Test
    fun `concurrent fifo consumption does not overspend lots`() {
        fifoService.addLot(
            walletAddress = walletAddress,
            tokenSymbol = "ETH",
            quantity = BigDecimal("1.0"),
            unitCostKrw = BigDecimal("1000000"),
            rawTransactionId = null,
            acquisitionDate = Instant.parse("2026-02-01T00:00:00Z")
        )

        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)
        val consumedQuantities = ConcurrentLinkedQueue<BigDecimal>()
        val errors = ConcurrentLinkedQueue<Throwable>()

        val executor = Executors.newFixedThreadPool(2)
        repeat(2) {
            executor.submit {
                try {
                    startLatch.await(3, TimeUnit.SECONDS)
                    val result = fifoService.consume(walletAddress, "ETH", BigDecimal("0.7"))
                    consumedQuantities.add(result.consumedQuantity)
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdownNow()

        val totalConsumed = consumedQuantities.fold(BigDecimal.ZERO) { acc, qty -> acc + qty }
        val remaining = jdbcTemplate.queryForObject(
            "select coalesce(sum(remaining_qty), 0) from cost_basis_lots where wallet_address = ? and token_symbol = ?",
            BigDecimal::class.java,
            walletAddress,
            "ETH"
        ) ?: BigDecimal.ZERO

        assertTrue(totalConsumed <= BigDecimal("1.0"), "totalConsumed=$totalConsumed")
        assertTrue(remaining >= BigDecimal.ZERO, "remaining=$remaining")
        assertTrue((totalConsumed + remaining) <= BigDecimal("1.00000001"), "consumed=$totalConsumed remaining=$remaining")

        assertTrue(errors.isEmpty(), "errors=$errors")
    }

    @Test
    fun `address aware fifo consumption falls back to legacy symbol lots`() {
        val tokenAddress = "0x1111111111111111111111111111111111111111"

        fifoService.addLot(
            walletAddress = walletAddress,
            tokenSymbol = "USDC",
            chain = "ETHEREUM",
            tokenAddress = tokenAddress,
            quantity = BigDecimal("2.0"),
            unitCostKrw = BigDecimal("200"),
            rawTransactionId = null,
            acquisitionDate = Instant.parse("2026-02-01T00:00:00Z")
        )
        fifoService.addLot(
            walletAddress = walletAddress,
            tokenSymbol = "USDC",
            quantity = BigDecimal("1.0"),
            unitCostKrw = BigDecimal("100"),
            rawTransactionId = null,
            acquisitionDate = Instant.parse("2026-02-02T00:00:00Z")
        )

        val result = fifoService.consume(
            walletAddress = walletAddress,
            tokenSymbol = "USDC",
            quantity = BigDecimal("2.5"),
            chain = "ETHEREUM",
            tokenAddress = tokenAddress
        )

        assertEquals(0, result.consumedQuantity.compareTo(BigDecimal("2.5")))
        assertEquals(0, result.missingQuantity.compareTo(BigDecimal.ZERO))
        assertEquals(0, result.totalCostKrw.compareTo(BigDecimal("450.00000000")))

        val remainingLegacy = jdbcTemplate.queryForObject(
            """
            select coalesce(sum(remaining_qty), 0)
            from cost_basis_lots
            where wallet_address = ? and token_symbol = ? and chain is null and token_address is null
            """.trimIndent(),
            BigDecimal::class.java,
            walletAddress,
            "USDC"
        ) ?: BigDecimal.ZERO
        assertEquals(0, remainingLegacy.compareTo(BigDecimal("0.5")))
    }
}
