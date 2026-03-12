package com.example.ledger.integration

import com.example.ledger.adapter.ethereum.EthereumRpcException
import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.WalletRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SyncConcurrencyIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @MockBean
    private lateinit var pricePort: PricePort

    @Test
    fun `second concurrent sync call is skipped while first sync is in progress`() {
        val address = "0xdddddddddddddddddddddddddddddddddddddddd"
        walletRepository.save(Wallet(address = address, syncStatus = SyncStatus.COMPLETED))

        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)

        whenever(blockchainDataPort.fetchTransactions(eq(address), anyOrNull())).thenAnswer {
            fetchStarted.countDown()
            assertTrue(releaseFetch.await(5, TimeUnit.SECONDS))
            emptyList<com.example.ledger.domain.model.RawTransaction>()
        }

        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit { syncPipelineUseCase.sync(address) }
            assertTrue(fetchStarted.await(5, TimeUnit.SECONDS))
            val second = executor.submit { syncPipelineUseCase.sync(address) }
            second.get(5, TimeUnit.SECONDS)
            releaseFetch.countDown()
            first.get(5, TimeUnit.SECONDS)
        } finally {
            releaseFetch.countDown()
            executor.shutdownNow()
        }

        verify(blockchainDataPort, times(1)).fetchTransactions(eq(address), anyOrNull())

        val wallet = walletRepository.findByAddress(address)
        assertNotNull(wallet)
        assertEquals(SyncStatus.COMPLETED, wallet.syncStatus)
    }

    @Test
    fun `wallet status transitions to failed when rpc throws during sync`() {
        val address = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        walletRepository.save(Wallet(address = address, syncStatus = SyncStatus.PENDING))

        whenever(blockchainDataPort.fetchTransactions(eq(address), anyOrNull())).thenThrow(
            EthereumRpcException(-32000, "upstream unavailable")
        )

        assertFailsWith<EthereumRpcException> {
            syncPipelineUseCase.sync(address)
        }

        val wallet = walletRepository.findByAddress(address)
        assertNotNull(wallet)
        assertEquals(SyncStatus.FAILED, wallet.syncStatus)
    }
}
