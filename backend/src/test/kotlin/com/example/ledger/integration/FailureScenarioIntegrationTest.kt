package com.example.ledger.integration

import com.example.ledger.adapter.ethereum.EthereumRpcException
import com.example.ledger.application.usecase.IngestWalletUseCase
import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.model.PriceInfo
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.WalletRepository
import com.example.ledger.domain.service.FifoService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FailureScenarioIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var journalRepository: JournalRepository

    @Autowired
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @Autowired
    private lateinit var ingestWalletUseCase: IngestWalletUseCase

    @Autowired
    private lateinit var fifoService: FifoService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @MockBean
    private lateinit var pricePort: PricePort

    @Test
    fun `rpc error during sync marks wallet as failed`() {
        val address = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        walletRepository.save(Wallet(address = address))

        whenever(blockchainDataPort.fetchTransactions(eq(address), anyOrNull())).thenThrow(
            EthereumRpcException(-32000, "rpc failure")
        )

        assertFailsWith<EthereumRpcException> {
            syncPipelineUseCase.sync(address)
        }

        val wallet = walletRepository.findByAddress(address)
        assertNotNull(wallet)
        assertEquals(SyncStatus.FAILED, wallet.syncStatus)
    }

    @Test
    fun `wallet recovers to completed after re-register following rpc failure`() {
        val address = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        var fetchCount = 0
        whenever(blockchainDataPort.fetchTransactions(eq(address), anyOrNull())).thenAnswer {
            fetchCount += 1
            if (fetchCount == 1) {
                throw EthereumRpcException(-32000, "temporary outage")
            }
            emptyList<RawTransaction>()
        }

        ingestWalletUseCase.registerWallet(address)
        awaitWalletStatus(address, SyncStatus.FAILED)

        ingestWalletUseCase.registerWallet(address)
        awaitWalletStatus(address, SyncStatus.COMPLETED)
    }

    @Test
    fun `coingecko unavailable path still completes sync with zero priced journals`() {
        val address = "0xcccccccccccccccccccccccccccccccccccccccc"
        walletRepository.save(Wallet(address = address))

        whenever(blockchainDataPort.fetchTransactions(eq(address), anyOrNull())).thenReturn(
            listOf(
                rawTransfer(
                    walletAddress = address,
                    txHash = "0xprice-fallback",
                    blockNumber = 321L,
                    from = "0x1111111111111111111111111111111111111111",
                    to = address,
                    value = "1.0",
                    timestamp = Instant.parse("2026-02-12T00:00:00Z")
                )
            )
        )
        whenever(pricePort.getPrice(anyOrNull(), any(), any())).thenReturn(
            PriceInfo(
                tokenAddress = null,
                tokenSymbol = "ETH",
                date = LocalDate.of(2026, 2, 12),
                priceKrw = BigDecimal.ZERO,
                source = PriceSource.UNKNOWN
            )
        )

        syncPipelineUseCase.sync(address)

        val wallet = walletRepository.findByAddress(address)
        assertNotNull(wallet)
        assertEquals(SyncStatus.COMPLETED, wallet.syncStatus)

        val journals = journalRepository.findByFilters(size = 100)
        assertTrue(journals.isNotEmpty())
    }

    @Test
    fun `concurrent sync attempt is safely skipped`() {
        val address = "0xdddddddddddddddddddddddddddddddddddddddd"
        walletRepository.save(Wallet(address = address))

        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        whenever(blockchainDataPort.fetchTransactions(eq(address), anyOrNull())).thenAnswer {
            fetchStarted.countDown()
            assertTrue(releaseFetch.await(5, TimeUnit.SECONDS))
            emptyList<RawTransaction>()
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
    }

    @Test
    fun `concurrent wallet registration creates exactly one wallet row`() {
        val address = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        whenever(blockchainDataPort.fetchTransactions(eq(address), anyOrNull())).thenReturn(emptyList())

        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val executor = Executors.newFixedThreadPool(2)

        repeat(2) {
            executor.submit {
                try {
                    startLatch.await(3, TimeUnit.SECONDS)
                    ingestWalletUseCase.registerWallet(address)
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertTrue(errors.isEmpty(), "errors=$errors")
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wallets WHERE address = ?",
            Long::class.java,
            address
        )
        assertEquals(1L, count)
    }

    @Test
    fun `fifo concurrent consumption completes without unhandled errors`() {
        val walletAddress = "0xffffffffffffffffffffffffffffffffffffffff"
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
        val errors = ConcurrentLinkedQueue<Throwable>()
        val executor = Executors.newFixedThreadPool(2)

        repeat(2) {
            executor.submit {
                try {
                    startLatch.await(3, TimeUnit.SECONDS)
                    fifoService.consume(walletAddress, "ETH", BigDecimal("0.7"))
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertTrue(errors.isEmpty(), "errors=$errors")
    }

    private fun awaitWalletStatus(address: String, expected: SyncStatus, timeoutMillis: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val status = walletRepository.findByAddress(address)?.syncStatus
            if (status == expected) {
                return
            }
            Thread.sleep(100)
        }
        val finalStatus = walletRepository.findByAddress(address)?.syncStatus
        assertEquals(expected, finalStatus, "wallet=$address status=$finalStatus")
    }

    private fun rawTransfer(
        walletAddress: String,
        txHash: String,
        blockNumber: Long,
        from: String,
        to: String,
        value: String,
        timestamp: Instant
    ): RawTransaction {
        val weiValue = BigDecimal(value).multiply(BigDecimal.TEN.pow(18)).toBigInteger()
        val valueHex = "0x${weiValue.toString(16)}"

        val txNode = objectMapper.createObjectNode()
            .put("hash", txHash)
            .put("from", from)
            .put("to", to)
            .put("value", valueHex)

        val receiptNode = objectMapper.createObjectNode()
            .put("status", "0x1")
            .put("gasUsed", "0x5208")
            .put("effectiveGasPrice", "0x2540be400")
        receiptNode.putArray("logs")

        val rawData = objectMapper.createObjectNode()
        rawData.set<com.fasterxml.jackson.databind.JsonNode>("transaction", txNode)
        rawData.set<com.fasterxml.jackson.databind.JsonNode>("receipt", receiptNode)

        return RawTransaction(
            walletAddress = walletAddress,
            txHash = txHash,
            blockNumber = blockNumber,
            txIndex = 0,
            blockTimestamp = timestamp,
            rawData = rawData,
            txStatus = 1
        )
    }
}
