package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletBalanceSnapshot
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.AccountingEventRepository
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.RawTransactionRepository
import com.example.ledger.domain.port.WalletBalanceSnapshotRepository
import com.example.ledger.domain.port.WalletRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CutoffPipelineIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var rawTransactionRepository: RawTransactionRepository

    @Autowired
    private lateinit var accountingEventRepository: AccountingEventRepository

    @Autowired
    private lateinit var journalRepository: JournalRepository

    @Autowired
    private lateinit var walletBalanceSnapshotRepository: WalletBalanceSnapshotRepository

    @Autowired
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @MockBean
    private lateinit var pricePort: PricePort

    @Test
    fun `cutoff pipeline scans from cutoff plus one and remains idempotent`() {
        val walletAddress = "0x3333333333333333333333333333333333333333"
        val cutoffBlock = 100L
        walletRepository.save(
            Wallet(
                address = walletAddress,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                cutoffBlock = cutoffBlock
            )
        )

        val txAt101 = rawTransfer(
            walletAddress = walletAddress,
            txHash = "0xcutoff101",
            blockNumber = 101L,
            from = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            to = walletAddress,
            value = "1.0",
            timestamp = Instant.parse("2026-02-10T00:00:00Z")
        )

        whenever(blockchainDataPort.getNativeBalanceAtBlock(eq(walletAddress), eq(cutoffBlock))).thenReturn(java.math.BigInteger("1"))
        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), eq(101L))).thenReturn(listOf(txAt101))
        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), eq(102L))).thenReturn(emptyList())

        syncPipelineUseCase.sync(walletAddress)
        syncPipelineUseCase.sync(walletAddress)

        val savedWallet = walletRepository.findByAddress(walletAddress)
        assertNotNull(savedWallet)
        assertEquals(SyncStatus.COMPLETED, savedWallet.syncStatus)
        assertEquals(WalletSyncPhase.DELTA_COMPLETED, savedWallet.syncPhase)
        assertEquals(101L, savedWallet.deltaSyncedBlock)
        assertEquals(101L, savedWallet.lastSyncedBlock)

        val rawTransactions = rawTransactionRepository.findByWalletAddress(walletAddress)
        assertEquals(2, rawTransactions.size)

        val rawTxId = rawTransactions.first { it.txHash == "0xcutoff101" }.id
        assertNotNull(rawTxId)
        val events = accountingEventRepository.findByRawTransactionId(rawTxId)
        assertTrue(events.isNotEmpty())

        val journals = journalRepository.findByFilters(size = 100)
        assertEquals(2, journals.size)
        assertTrue(journals.any { it.description.startsWith("Cutoff opening balance") })
        assertTrue(journals.any { it.description == "Incoming ETH" })

        verify(blockchainDataPort).fetchTransactions(eq(walletAddress), eq(101L))
        verify(blockchainDataPort).fetchTransactions(eq(walletAddress), eq(102L))
        verifyNoInteractions(pricePort)
    }

    @Test
    fun `cutoff pipeline backfills opening journal for legacy snapped wallet`() {
        val walletAddress = "0x4444444444444444444444444444444444444444"
        val cutoffBlock = 200L
        val wallet = walletRepository.save(
            Wallet(
                address = walletAddress,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_COMPLETED,
                syncStatus = SyncStatus.PENDING,
                cutoffBlock = cutoffBlock,
                snapshotBlock = cutoffBlock,
                deltaSyncedBlock = cutoffBlock,
                lastSyncedBlock = cutoffBlock
            )
        )

        walletBalanceSnapshotRepository.saveAll(
            listOf(
                WalletBalanceSnapshot(
                    walletId = requireNotNull(wallet.id),
                    tokenAddress = "0x0000000000000000000000000000000000000000",
                    tokenSymbol = "ETH",
                    balanceRaw = java.math.BigInteger("1000000000000000000"),
                    cutoffBlock = cutoffBlock
                )
            )
        )

        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), eq(201L))).thenReturn(emptyList())

        syncPipelineUseCase.sync(walletAddress)
        syncPipelineUseCase.sync(walletAddress)

        val journals = journalRepository.findByFilters(size = 100)
        assertEquals(1, journals.size)
        assertTrue(journals.single().description.startsWith("Cutoff opening balance"))
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
        val weiValue = java.math.BigDecimal(value).multiply(java.math.BigDecimal.TEN.pow(18)).toBigInteger()
        val valueHex = "0x${weiValue.toString(16)}"

        val txNode = objectMapper.createObjectNode()
            .put("hash", txHash)
            .put("from", from)
            .put("to", to)
            .put("value", valueHex)

        val receiptNode = objectMapper.createObjectNode()
            .put("status", "0x1")
            .put("gasUsed", "0x0")
            .put("effectiveGasPrice", "0x0")
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
