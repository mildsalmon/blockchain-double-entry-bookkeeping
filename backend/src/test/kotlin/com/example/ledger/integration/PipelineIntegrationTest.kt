package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.model.PriceInfo
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.port.AccountingEventRepository
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.RawTransactionRepository
import com.example.ledger.domain.port.WalletRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PipelineIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var rawTransactionRepository: RawTransactionRepository

    @Autowired
    private lateinit var accountingEventRepository: AccountingEventRepository

    @Autowired
    private lateinit var journalRepository: JournalRepository

    @Autowired
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @MockBean
    private lateinit var pricePort: PricePort

    @Test
    fun `sync pipeline stores raw transactions events journals and supports incremental sync`() {
        val walletAddress = "0x1111111111111111111111111111111111111111"
        walletRepository.save(Wallet(address = walletAddress))

        val txTimestamp = Instant.parse("2026-02-01T10:00:00Z")
        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), anyOrNull())).thenReturn(
            listOf(
                rawTransfer(
                    walletAddress = walletAddress,
                    txHash = "0xaaaabbbbccccdddd",
                    blockNumber = 101L,
                    from = "0x9999999999999999999999999999999999999999",
                    to = walletAddress,
                    value = "1.25",
                    timestamp = txTimestamp
                )
            )
        )
        whenever(pricePort.getPrice(anyOrNull(), any(), any())).thenReturn(
            PriceInfo(
                tokenAddress = null,
                tokenSymbol = "ETH",
                date = LocalDate.of(2026, 2, 1),
                priceKrw = BigDecimal("4100000"),
                source = PriceSource.COINGECKO
            )
        )

        syncPipelineUseCase.sync(walletAddress)

        val syncedWallet = walletRepository.findByAddress(walletAddress)
        assertNotNull(syncedWallet)
        assertEquals(SyncStatus.COMPLETED, syncedWallet.syncStatus)
        assertEquals(101L, syncedWallet.lastSyncedBlock)

        val rawTransactions = rawTransactionRepository.findByWalletAddress(walletAddress)
        assertEquals(1, rawTransactions.size)

        val rawTxId = rawTransactions.first().id
        assertNotNull(rawTxId)
        val events = accountingEventRepository.findByRawTransactionId(rawTxId)
        assertTrue(events.isNotEmpty())

        val journals = journalRepository.findByFilters(size = 100)
        assertTrue(journals.isNotEmpty())
        journals.forEach { entry ->
            val debitTotal = entry.lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.debitAmount }
            val creditTotal = entry.lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.creditAmount }
            assertEquals(0, debitTotal.compareTo(creditTotal))
        }

        syncPipelineUseCase.sync(walletAddress)
        val rawTransactionsAfterSecondSync = rawTransactionRepository.findByWalletAddress(walletAddress)
        val journalsAfterSecondSync = journalRepository.findByFilters(size = 100)

        assertEquals(1, rawTransactionsAfterSecondSync.size)
        assertEquals(journals.size, journalsAfterSecondSync.size)
    }

    @Test
    fun `sync pipeline continues when token price is unknown and writes zero valuation`() {
        val walletAddress = "0x2222222222222222222222222222222222222222"
        walletRepository.save(Wallet(address = walletAddress))

        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), anyOrNull())).thenReturn(
            listOf(
                rawTransfer(
                    walletAddress = walletAddress,
                    txHash = "0xeeeeffffaaaa1111",
                    blockNumber = 202L,
                    from = "0x9999999999999999999999999999999999999999",
                    to = walletAddress,
                    value = "5.0",
                    timestamp = Instant.parse("2026-02-02T00:00:00Z")
                )
            )
        )
        whenever(pricePort.getPrice(anyOrNull(), any(), any())).thenReturn(
            PriceInfo(
                tokenAddress = null,
                tokenSymbol = "ETH",
                date = LocalDate.of(2026, 2, 2),
                priceKrw = BigDecimal.ZERO,
                source = PriceSource.UNKNOWN
            )
        )

        syncPipelineUseCase.sync(walletAddress)

        val syncedWallet = walletRepository.findByAddress(walletAddress)
        assertNotNull(syncedWallet)
        assertEquals(SyncStatus.COMPLETED, syncedWallet.syncStatus)

        val journals = journalRepository.findByFilters(size = 100)
        assertTrue(journals.isNotEmpty())
    }

    @Test
    fun `sync pipeline processes transactions in block order before fifo calculations`() {
        val walletAddress = "0x4444444444444444444444444444444444444444"
        walletRepository.save(Wallet(address = walletAddress))

        val incoming = rawTransfer(
            walletAddress = walletAddress,
            txHash = "0xincoming",
            blockNumber = 100L,
            txIndex = 0,
            from = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            to = walletAddress,
            value = "1.0",
            timestamp = Instant.parse("2026-02-01T00:00:00Z"),
            gasUsedHex = "0x0",
            effectiveGasPriceHex = "0x0"
        )

        val outgoing = rawTransfer(
            walletAddress = walletAddress,
            txHash = "0xoutgoing",
            blockNumber = 200L,
            txIndex = 0,
            from = walletAddress,
            to = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            value = "1.0",
            timestamp = Instant.parse("2026-02-02T00:00:00Z"),
            gasUsedHex = "0x0",
            effectiveGasPriceHex = "0x0"
        )

        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), anyOrNull())).thenReturn(
            listOf(outgoing, incoming)
        )

        whenever(pricePort.getPrice(anyOrNull(), any(), any())).thenAnswer { invocation ->
            val date = invocation.getArgument<LocalDate>(2)
            val price = when (date) {
                LocalDate.of(2026, 2, 1) -> BigDecimal("100")
                LocalDate.of(2026, 2, 2) -> BigDecimal("150")
                else -> BigDecimal.ZERO
            }

            PriceInfo(
                tokenAddress = null,
                tokenSymbol = "ETH",
                date = date,
                priceKrw = price,
                source = PriceSource.COINGECKO
            )
        }

        syncPipelineUseCase.sync(walletAddress)

        val journals = journalRepository.findByFilters(size = 100)
        val outgoingJournal = journals.first { it.description == "Outgoing ETH" }
        val realizedGain = outgoingJournal.lines.firstOrNull { it.accountCode == "수익:실현이익" }

        assertNotNull(realizedGain)
        assertEquals(0, realizedGain.creditAmount.compareTo(BigDecimal("50.00000000")))
    }

    private fun rawTransfer(
        walletAddress: String,
        txHash: String,
        blockNumber: Long,
        txIndex: Int = 1,
        from: String,
        to: String,
        value: String,
        timestamp: Instant,
        gasUsedHex: String = "0x5208",
        effectiveGasPriceHex: String = "0x2540be400"
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
            .put("gasUsed", gasUsedHex)
            .put("effectiveGasPrice", effectiveGasPriceHex)
        receiptNode.putArray("logs")

        val rawData = objectMapper.createObjectNode()
        rawData.set<com.fasterxml.jackson.databind.JsonNode>("transaction", txNode)
        rawData.set<com.fasterxml.jackson.databind.JsonNode>("receipt", receiptNode)

        return RawTransaction(
            walletAddress = walletAddress,
            txHash = txHash,
            blockNumber = blockNumber,
            txIndex = txIndex,
            blockTimestamp = timestamp,
            rawData = rawData,
            txStatus = 1
        )
    }
}
