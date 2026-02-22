package com.example.ledger.integration

import com.example.ledger.adapter.persistence.spring.SpringDataAuditLogRepository
import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.RawTransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalApiIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var rawTransactionRepository: RawTransactionRepository

    @Autowired
    private lateinit var journalRepository: JournalRepository

    @Autowired
    private lateinit var auditLogRepository: SpringDataAuditLogRepository

    @Test
    fun `can update approve and block edit for approved journal`() {
        val journalId = seedJournalEntry(status = JournalStatus.REVIEW_REQUIRED)

        val patchPayload = mapOf(
            "memo" to "검토 메모",
            "lines" to listOf(
                mapOf(
                    "accountCode" to "자산:암호화폐:ETH",
                    "debitAmount" to 1500.0,
                    "creditAmount" to 0.0,
                    "tokenSymbol" to "ETH",
                    "tokenQuantity" to 0.3
                ),
                mapOf(
                    "accountCode" to "수익:에어드롭",
                    "debitAmount" to 0.0,
                    "creditAmount" to 1500.0,
                    "tokenSymbol" to "ETH",
                    "tokenQuantity" to 0.3
                )
            )
        )

        mockMvc.patch("/api/journals/$journalId") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(patchPayload)
        }.andExpect {
            status { isOk() }
            jsonPath("$.memo") { value("검토 메모") }
        }

        mockMvc.post("/api/journals/$journalId/approve") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
        }

        mockMvc.patch("/api/journals/$journalId") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(patchPayload)
        }.andExpect {
            status { isBadRequest() }
        }

        val logs = auditLogRepository.findAll().map { it.action }
        assertTrue(logs.contains("UPDATE"))
        assertTrue(logs.contains("APPROVE"))

        val approved = journalRepository.findById(journalId)
        assertEquals(JournalStatus.APPROVED, approved?.status)
    }

    private fun seedJournalEntry(status: JournalStatus): Long {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x3333333333333333333333333333333333333333",
                    txHash = "0xjournal-seed-tx",
                    blockNumber = 1,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-03T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()

        val saved = journalRepository.save(
            JournalEntry(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                entryDate = Instant.parse("2026-02-03T00:00:00Z"),
                description = "seed journal",
                status = status,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ETH",
                        debitAmount = BigDecimal("1000"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("0.25")
                    ),
                    JournalLine(
                        accountCode = "수익:에어드롭",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("1000"),
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("0.25")
                    )
                )
            )
        )

        return saved.id ?: error("journal id should not be null")
    }
}
