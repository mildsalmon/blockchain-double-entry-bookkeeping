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

        // At most one serialization/locking error is expected under SERIALIZABLE race.
        assertTrue(errors.size <= 1, "errors=$errors")
    }
}
