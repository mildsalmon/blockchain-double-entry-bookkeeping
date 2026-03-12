package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.Wallet
import com.example.ledger.adapter.coingecko.CoinGeckoClient
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.RawTransactionRepository
import com.example.ledger.domain.port.WalletRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

class PerformanceIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var rawTransactionRepository: RawTransactionRepository

    @Autowired
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @Autowired
    private lateinit var pricePort: PricePort

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @MockBean
    private lateinit var coinGeckoClient: CoinGeckoClient

    @Test
    fun `processes 1000 transactions within five minutes`() {
        val walletAddress = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        walletRepository.save(Wallet(address = walletAddress))

        val transactions = (1..1000).map { index ->
            val txHash = "0x${index.toString(16).padStart(64, '0')}"
            rawTransfer(
                walletAddress = walletAddress,
                txHash = txHash,
                blockNumber = 10_000L + index,
                from = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                to = walletAddress,
                value = "0.01",
                timestamp = Instant.parse("2026-02-01T00:00:00Z")
            )
        }

        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), anyOrNull())).thenReturn(transactions)
        whenever(coinGeckoClient.fetchRangePrices(eq("ethereum"), any(), any())).thenAnswer { invocation ->
            val fromDate = invocation.arguments[1] as LocalDate
            mapOf(fromDate to BigDecimal("4000000"))
        }

        assertTimeoutPreemptively(Duration.ofMinutes(5)) {
            syncPipelineUseCase.sync(walletAddress)
        }

        val saved = rawTransactionRepository.findByWalletAddress(walletAddress)
        assertEquals(1000, saved.size)
    }

    @Test
    fun `resolves 500 date prices within two minutes using range API`() {
        val fromDate = LocalDate.of(2025, 1, 1)
        val toDate = fromDate.plusDays(499)

        val mockedPrices = mutableMapOf<LocalDate, BigDecimal>()
        var current = fromDate
        while (!current.isAfter(toDate)) {
            mockedPrices[current] = BigDecimal("1000")
            current = current.plusDays(1)
        }

        whenever(coinGeckoClient.fetchRangePrices(eq("ethereum"), eq(fromDate), eq(toDate))).thenReturn(mockedPrices)

        val result = assertTimeoutPreemptively(Duration.ofMinutes(2)) {
            pricePort.getPricesInRange(
                tokenAddress = null,
                tokenSymbol = "ETH",
                fromDate = fromDate,
                toDate = toDate
            )
        }

        assertEquals(500, result.size)
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
            txIndex = 1,
            blockTimestamp = timestamp,
            rawData = rawData,
            txStatus = 1
        )
    }
}
