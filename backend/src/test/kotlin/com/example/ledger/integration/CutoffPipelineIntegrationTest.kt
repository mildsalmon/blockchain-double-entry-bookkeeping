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
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val ERC20_TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
private const val UNISWAP_V2_SWAP_TOPIC = "0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822"

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

    @Test
    fun `cutoff delta transfer normalizes token symbol and decimals`() {
        val walletAddress = "0x5555555555555555555555555555555555555555"
        val tokenAddress = "0x9999999999999999999999999999999999999999"
        val cutoffBlock = 300L
        walletRepository.save(
            Wallet(
                address = walletAddress,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                cutoffBlock = cutoffBlock
            )
        )

        whenever(blockchainDataPort.getNativeBalanceAtBlock(eq(walletAddress), eq(cutoffBlock))).thenReturn(BigInteger.ZERO)
        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), eq(cutoffBlock + 1)))
            .thenReturn(
                listOf(
                    rawErc20IncomingTransfer(
                        walletAddress = walletAddress,
                        tokenAddress = tokenAddress,
                        txHash = "0xcutoff-usdc-incoming",
                        blockNumber = cutoffBlock + 1,
                        from = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        to = walletAddress,
                        amountRaw = BigInteger("1500000"),
                        timestamp = Instant.parse("2026-02-12T00:00:00Z")
                    )
                )
            )
        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), eq(cutoffBlock + 2))).thenReturn(emptyList())
        whenever(blockchainDataPort.getTokenSymbol(eq(tokenAddress), eq(cutoffBlock + 1))).thenReturn("USDC")
        whenever(blockchainDataPort.getTokenDecimals(eq(tokenAddress), eq(cutoffBlock + 1))).thenReturn(6)

        syncPipelineUseCase.sync(walletAddress)
        syncPipelineUseCase.sync(walletAddress)

        val journals = journalRepository.findByFilters(size = 100)
        val incomingUsdc = journals.first { it.description == "Incoming USDC" }
        val assetLine = incomingUsdc.lines.first { it.accountCode == "자산:암호화폐:ERC20:USDC" }
        val quantity = requireNotNull(assetLine.tokenQuantity)
        assertEquals(0, quantity.compareTo(BigDecimal("1.5")))
    }

    @Test
    fun `cutoff delta swap normalizes both token in and token out amounts`() {
        val walletAddress = "0x6666666666666666666666666666666666666666"
        val tokenInAddress = "0x1111111111111111111111111111111111111111"
        val tokenOutAddress = "0x2222222222222222222222222222222222222222"
        val cutoffBlock = 400L
        walletRepository.save(
            Wallet(
                address = walletAddress,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                cutoffBlock = cutoffBlock
            )
        )

        whenever(blockchainDataPort.getNativeBalanceAtBlock(eq(walletAddress), eq(cutoffBlock))).thenReturn(BigInteger.ZERO)
        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), eq(cutoffBlock + 1)))
            .thenReturn(
                listOf(
                    rawUniswapV2Swap(
                        walletAddress = walletAddress,
                        txHash = "0xcutoff-swap",
                        blockNumber = cutoffBlock + 1,
                        token0Address = tokenInAddress,
                        token1Address = tokenOutAddress,
                        amount0InRaw = BigInteger("1500000"),
                        amount1InRaw = BigInteger.ZERO,
                        amount0OutRaw = BigInteger.ZERO,
                        amount1OutRaw = BigInteger("250000000000000000"),
                        timestamp = Instant.parse("2026-02-13T00:00:00Z")
                    )
                )
            )
        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), eq(cutoffBlock + 2))).thenReturn(emptyList())
        whenever(blockchainDataPort.getTokenSymbol(eq(tokenInAddress), eq(cutoffBlock + 1))).thenReturn("USDC")
        whenever(blockchainDataPort.getTokenDecimals(eq(tokenInAddress), eq(cutoffBlock + 1))).thenReturn(6)
        whenever(blockchainDataPort.getTokenSymbol(eq(tokenOutAddress), eq(cutoffBlock + 1))).thenReturn("WETH")
        whenever(blockchainDataPort.getTokenDecimals(eq(tokenOutAddress), eq(cutoffBlock + 1))).thenReturn(18)

        syncPipelineUseCase.sync(walletAddress)

        val journals = journalRepository.findByFilters(size = 100)
        val swapEntry = journals.first { it.description == "Swap USDC -> WETH" }
        val tokenOutLine = swapEntry.lines.first { it.accountCode == "자산:암호화폐:ERC20:WETH" }
        val tokenInLine = swapEntry.lines.first { it.accountCode == "자산:암호화폐:ERC20:USDC" }

        assertEquals(0, requireNotNull(tokenOutLine.tokenQuantity).compareTo(BigDecimal("0.25")))
        assertEquals(0, requireNotNull(tokenInLine.tokenQuantity).compareTo(BigDecimal("-1.5")))
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

    private fun rawErc20IncomingTransfer(
        walletAddress: String,
        tokenAddress: String,
        txHash: String,
        blockNumber: Long,
        from: String,
        to: String,
        amountRaw: BigInteger,
        timestamp: Instant
    ): RawTransaction {
        val txNode = objectMapper.createObjectNode()
            .put("hash", txHash)
            .put("from", from)
            .put("to", tokenAddress)
            .put("value", "0x0")

        val receiptNode = objectMapper.createObjectNode()
            .put("status", "0x1")
            .put("gasUsed", "0x0")
            .put("effectiveGasPrice", "0x0")

        val logs = receiptNode.putArray("logs")
        val transferLog = logs.addObject()
        transferLog.put("address", tokenAddress)
        transferLog.put("data", "0x${amountRaw.toString(16).padStart(64, '0')}")
        val topics = transferLog.putArray("topics")
        topics.add(ERC20_TRANSFER_TOPIC)
        topics.add(toTopicAddress(from))
        topics.add(toTopicAddress(to))

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

    private fun toTopicAddress(address: String): String {
        val clean = address.removePrefix("0x").lowercase().padStart(64, '0')
        return "0x$clean"
    }

    private fun rawUniswapV2Swap(
        walletAddress: String,
        txHash: String,
        blockNumber: Long,
        token0Address: String,
        token1Address: String,
        amount0InRaw: BigInteger,
        amount1InRaw: BigInteger,
        amount0OutRaw: BigInteger,
        amount1OutRaw: BigInteger,
        timestamp: Instant
    ): RawTransaction {
        val txNode = objectMapper.createObjectNode()
            .put("hash", txHash)
            .put("from", walletAddress)
            .put("to", "0xpool")
            .put("value", "0x0")

        val receiptNode = objectMapper.createObjectNode()
            .put("status", "0x1")
            .put("gasUsed", "0x0")
            .put("effectiveGasPrice", "0x0")

        val swapData = buildString {
            append("0x")
            append(amount0InRaw.toString(16).padStart(64, '0'))
            append(amount1InRaw.toString(16).padStart(64, '0'))
            append(amount0OutRaw.toString(16).padStart(64, '0'))
            append(amount1OutRaw.toString(16).padStart(64, '0'))
        }

        val logs = receiptNode.putArray("logs")
        val swapLog = logs.addObject()
        swapLog.put("address", "0xpool")
        swapLog.put("data", swapData)
        swapLog.put("token0", token0Address)
        swapLog.put("token1", token1Address)
        swapLog.put("token0Symbol", "TOKEN0")
        swapLog.put("token1Symbol", "TOKEN1")
        val topics = swapLog.putArray("topics")
        topics.add(UNISWAP_V2_SWAP_TOPIC)

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
