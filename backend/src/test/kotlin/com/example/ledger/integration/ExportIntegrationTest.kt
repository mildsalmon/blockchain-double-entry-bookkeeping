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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var rawTransactionRepository: RawTransactionRepository

    @Autowired
    private lateinit var journalRepository: JournalRepository

    @Test
    fun `exports csv and xlsx formats`() {
        seedJournalEntry()

        val csvPayload = mapOf(
            "fromDate" to "2026-02-01",
            "toDate" to "2026-02-28",
            "format" to "CSV"
        )

        val csvResponse = mockMvc.post("/api/export") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(csvPayload)
        }.andExpect {
            status { isOk() }
            content { contentType("text/csv") }
        }.andReturn().response.contentAsByteArray

        assertTrue(csvResponse.size > 3)
        assertEquals(0xEF.toByte(), csvResponse[0])
        assertEquals(0xBB.toByte(), csvResponse[1])
        assertEquals(0xBF.toByte(), csvResponse[2])

        val xlsxPayload = mapOf(
            "fromDate" to "2026-02-01",
            "toDate" to "2026-02-28",
            "format" to "XLSX"
        )

        val xlsxResponse = mockMvc.post("/api/export") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(xlsxPayload)
        }.andExpect {
            status { isOk() }
            content { contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
        }.andReturn().response.contentAsByteArray

        assertTrue(xlsxResponse.size > 4)
        assertEquals('P'.code.toByte(), xlsxResponse[0])
        assertEquals('K'.code.toByte(), xlsxResponse[1])
    }

    private fun seedJournalEntry() {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x4444444444444444444444444444444444444444",
                    txHash = "0xexport-seed-tx",
                    blockNumber = 9,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-10T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()

        journalRepository.save(
            JournalEntry(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                entryDate = Instant.parse("2026-02-10T00:00:00Z"),
                description = "export journal",
                status = JournalStatus.APPROVED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ETH",
                        debitAmount = BigDecimal("2000"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("0.5")
                    ),
                    JournalLine(
                        accountCode = "수익:에어드롭",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("2000"),
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("0.5")
                    )
                )
            )
        )
    }
}
