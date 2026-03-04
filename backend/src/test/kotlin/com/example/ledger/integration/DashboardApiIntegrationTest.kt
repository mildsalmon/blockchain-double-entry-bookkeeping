package com.example.ledger.integration

import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.RawTransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant

class DashboardApiIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var rawTransactionRepository: RawTransactionRepository

    @Autowired
    private lateinit var journalRepository: JournalRepository

    @Test
    fun `dashboard balances aggregates quantity for mixed legacy and signed entries`() {
        val walletAddress = "0x7777777777777777777777777777777777777777"

        val incomingRaw = seedRawTx(walletAddress, "0xdash-incoming", Instant.parse("2026-02-20T00:00:00Z"))
        val outgoingRaw = seedRawTx(walletAddress, "0xdash-outgoing", Instant.parse("2026-02-21T00:00:00Z"))

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(incomingRaw.id),
                entryDate = incomingRaw.blockTimestamp,
                description = "Incoming ETH",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ETH",
                        debitAmount = BigDecimal("1000"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("1.0")
                    ),
                    JournalLine(
                        accountCode = "수익:미지정수입",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("1000"),
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("1.0")
                    )
                )
            )
        )

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(outgoingRaw.id),
                entryDate = outgoingRaw.blockTimestamp,
                description = "Outgoing ETH",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ETH",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("500"),
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("0.4")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal("500"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("0.4")
                    )
                )
            )
        )

        mockMvc.get("/api/dashboard/balances") {
            param("walletAddress", walletAddress)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.walletCount") { value(1) }
            jsonPath("$.summary.tokenCount") { value(1) }
            jsonPath("$.summary.positionCount") { value(1) }
            jsonPath("$.positions[0].walletAddress") { value(walletAddress) }
            jsonPath("$.positions[0].accountCode") { value("자산:암호화폐:ETH") }
            jsonPath("$.positions[0].tokenSymbol") { value("ETH") }
            jsonPath("$.positions[0].quantity") { value("0.6") }
        }
    }

    @Test
    fun `dashboard balances orders positions by wallet then account then token`() {
        val walletAddress = "0x8888888888888888888888888888888888888888"

        val rawA = seedRawTx(walletAddress, "0xdash-order-a", Instant.parse("2026-02-22T00:00:00Z"))
        val rawB = seedRawTx(walletAddress, "0xdash-order-b", Instant.parse("2026-02-23T00:00:00Z"))

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(rawA.id),
                entryDate = rawA.blockTimestamp,
                description = "Incoming WBTC",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ERC20:*",
                        debitAmount = BigDecimal("1000"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "WBTC",
                        tokenQuantity = BigDecimal("0.5")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("1000"),
                        tokenSymbol = "WBTC",
                        tokenQuantity = BigDecimal("0.5")
                    )
                )
            )
        )

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(rawB.id),
                entryDate = rawB.blockTimestamp,
                description = "Incoming USDC",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ETH",
                        debitAmount = BigDecimal("500"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "USDC",
                        tokenQuantity = BigDecimal("10")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("500"),
                        tokenSymbol = "USDC",
                        tokenQuantity = BigDecimal("10")
                    )
                )
            )
        )

        mockMvc.get("/api/dashboard/balances") {
            param("walletAddress", walletAddress)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.positionCount") { value(2) }
            jsonPath("$.positions[0].accountCode") { value("자산:암호화폐:ERC20:*") }
            jsonPath("$.positions[0].tokenSymbol") { value("WBTC") }
            jsonPath("$.positions[1].accountCode") { value("자산:암호화폐:ETH") }
            jsonPath("$.positions[1].tokenSymbol") { value("USDC") }
        }
    }

    private fun seedRawTx(walletAddress: String, txHash: String, timestamp: Instant): RawTransaction {
        return rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = walletAddress,
                    txHash = txHash,
                    blockNumber = 100,
                    txIndex = 0,
                    blockTimestamp = timestamp,
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()
    }
}
