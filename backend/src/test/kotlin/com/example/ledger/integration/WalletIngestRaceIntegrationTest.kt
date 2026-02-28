package com.example.ledger.integration

import com.example.ledger.application.usecase.IngestWalletUseCase
import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.port.WalletRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WalletIngestRaceIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var ingestWalletUseCase: IngestWalletUseCase

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @MockBean
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @Test
    fun `concurrent registration of same address results in exactly one wallet row`() {
        val address = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val threadCount = 2
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errors = mutableListOf<Throwable>()

        val executor = Executors.newFixedThreadPool(threadCount)
        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await(3, TimeUnit.SECONDS)
                    ingestWalletUseCase.registerWallet(address)
                } catch (t: Throwable) {
                    synchronized(errors) { errors.add(t) }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdownNow()

        // No thread should have thrown
        assertEquals(emptyList(), errors, "Expected no errors but got: $errors")

        // Exactly one wallet row must exist
        val saved = walletRepository.findByAddress(address)
        assertNotNull(saved, "Wallet should exist in DB")

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wallets WHERE address = ?",
            Long::class.java,
            address
        )
        assertEquals(1L, count, "Expected exactly 1 wallet row but found $count")
        verify(syncPipelineUseCase, times(2)).syncAsync(address)
    }
}
