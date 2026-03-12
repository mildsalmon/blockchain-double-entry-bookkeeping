package com.example.ledger.integration

import com.example.ledger.adapter.persistence.spring.SpringDataAuditLogRepository
import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.PriceInfo
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.port.AccountingEventRepository
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.RawTransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.nullValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
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
    private lateinit var accountingEventRepository: AccountingEventRepository

    @Autowired
    private lateinit var journalRepository: JournalRepository

    @Autowired
    private lateinit var auditLogRepository: SpringDataAuditLogRepository

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @MockBean
    private lateinit var pricePort: PricePort

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
                    "tokenQuantity" to 0.25
                ),
                mapOf(
                    "accountCode" to "수익:에어드롭",
                    "debitAmount" to 0.0,
                    "creditAmount" to 1500.0,
                    "tokenSymbol" to "ETH",
                    "tokenQuantity" to 0.25
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

    @Test
    fun `rejects journal update when token quantity changes`() {
        val journalId = seedJournalEntry(status = JournalStatus.REVIEW_REQUIRED)

        val patchPayload = mapOf(
            "memo" to "토큰 수량 변경 시도",
            "lines" to listOf(
                mapOf(
                    "accountCode" to "자산:암호화폐:ETH",
                    "debitAmount" to 1000.0,
                    "creditAmount" to 0.0,
                    "tokenSymbol" to "ETH",
                    "tokenQuantity" to 0.3
                ),
                mapOf(
                    "accountCode" to "수익:에어드롭",
                    "debitAmount" to 0.0,
                    "creditAmount" to 1000.0,
                    "tokenSymbol" to "ETH",
                    "tokenQuantity" to 0.3
                )
            )
        )

        mockMvc.patch("/api/journals/$journalId") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(patchPayload)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `journal detail keeps non token lines null on read and memo update`() {
        val journalId = seedJournalEntryWithNonTokenOffset(status = JournalStatus.REVIEW_REQUIRED)

        val detailResponse = mockMvc.get("/api/journals/$journalId")
            .andExpect {
                status { isOk() }
                jsonPath("$.journal.lines[1].tokenSymbol") { value(nullValue()) }
                jsonPath("$.journal.lines[1].chain") { value(nullValue()) }
                jsonPath("$.journal.lines[1].tokenAddress") { value(nullValue()) }
                jsonPath("$.journal.lines[1].displayLabel") { value(nullValue()) }
            }
            .andReturn()
            .response
            .contentAsString

        val detailJson = objectMapper.readTree(detailResponse)
        val patchLines = objectMapper.createArrayNode()
        detailJson.at("/journal/lines").forEach { line ->
            patchLines.add(
                objectMapper.createObjectNode()
                    .put("accountCode", line["accountCode"].asText())
                    .put("debitAmount", line["debitAmount"].decimalValue())
                    .put("creditAmount", line["creditAmount"].decimalValue())
                    .put("tokenSymbol", line["tokenSymbol"]?.takeUnless { it.isNull }?.asText())
                    .put("chain", line["chain"]?.takeUnless { it.isNull }?.asText())
                    .put("tokenAddress", line["tokenAddress"]?.takeUnless { it.isNull }?.asText())
                    .put("tokenQuantity", line["tokenQuantity"]?.takeUnless { it.isNull }?.decimalValue())
            )
        }
        val patchPayload = objectMapper.createObjectNode().put("memo", "메모만 수정")
        patchPayload.set<com.fasterxml.jackson.databind.JsonNode>("lines", patchLines)

        mockMvc.patch("/api/journals/$journalId") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(patchPayload)
        }.andExpect {
            status { isOk() }
            jsonPath("$.memo") { value("메모만 수정") }
            jsonPath("$.lines[1].tokenSymbol") { value(nullValue()) }
            jsonPath("$.lines[1].chain") { value(nullValue()) }
            jsonPath("$.lines[1].tokenAddress") { value(nullValue()) }
            jsonPath("$.lines[1].displayLabel") { value(nullValue()) }
        }

        val saved = journalRepository.findById(journalId) ?: error("journal not found")
        assertEquals(null, saved.lines[1].tokenSymbol)
        assertEquals(null, saved.lines[1].chain)
        assertEquals(null, saved.lines[1].tokenAddress)
    }

    @Test
    fun `rejects journal update when token address changes`() {
        val journalId = seedAddressAwareJournalEntry(status = JournalStatus.REVIEW_REQUIRED)

        val patchPayload = mapOf(
            "memo" to "토큰 주소 변경 시도",
            "lines" to listOf(
                mapOf(
                    "accountCode" to "자산:암호화폐:ERC20:USDC@0x2222222222222222222222222222222222222222",
                    "debitAmount" to 1000.0,
                    "creditAmount" to 0.0,
                    "tokenSymbol" to "USDC",
                    "chain" to "ETHEREUM",
                    "tokenAddress" to "0x2222222222222222222222222222222222222222",
                    "tokenQuantity" to 0.25
                ),
                mapOf(
                    "accountCode" to "수익:에어드롭",
                    "debitAmount" to 0.0,
                    "creditAmount" to 1000.0,
                    "tokenSymbol" to "USDC",
                    "chain" to "ETHEREUM",
                    "tokenAddress" to "0x1111111111111111111111111111111111111111",
                    "tokenQuantity" to 0.25
                )
            )
        )

        mockMvc.patch("/api/journals/$journalId") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(patchPayload)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `manual classify accepts token address and stores address aware identity`() {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x6666666666666666666666666666666666666666",
                    txHash = "0xmanual-classify-address",
                    blockNumber = 4,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-06T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()
        val tokenAddress = "0x9999999999999999999999999999999999999999"

        val event = accountingEventRepository.save(
            AccountingEvent(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                eventType = EventType.UNCLASSIFIED,
                classifierId = "seed",
                tokenSymbol = "USDC",
                amountRaw = BigDecimal("1.25").toBigInteger(),
                amountDecimal = BigDecimal("1.25"),
                priceKrw = BigDecimal.ZERO,
                priceSource = PriceSource.UNKNOWN
            )
        )

        whenever(blockchainDataPort.getTokenSymbol(eq(tokenAddress), anyOrNull())).thenReturn("USDC")
        mockPrice(tokenAddress, "USDC", LocalDate.of(2026, 2, 6), BigDecimal("1350"))

        mockMvc.post("/api/unclassified/${event.id}/classify") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "eventType" to "INCOMING",
                    "tokenSymbol" to "USDC",
                    "amountDecimal" to "1.25",
                    "tokenAddress" to tokenAddress
                )
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("MANUAL_CLASSIFIED") }
            jsonPath("$.description") { value("Incoming USDC") }
        }

        val updatedEvent = accountingEventRepository.findById(event.id ?: error("event id should not be null"))
            ?: error("accounting event not found")
        kotlin.test.assertEquals(tokenAddress, updatedEvent.tokenAddress)
        kotlin.test.assertEquals("USDC", updatedEvent.tokenSymbol)
        kotlin.test.assertEquals(0, updatedEvent.priceKrw?.compareTo(BigDecimal("1350")))
        kotlin.test.assertEquals(PriceSource.COINGECKO, updatedEvent.priceSource)
    }

    @Test
    fun `manual classify can clear token address and normalize native symbol`() {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x7777777777777777777777777777777777777777",
                    txHash = "0xmanual-classify-clear-address",
                    blockNumber = 5,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-07T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()
        val existingAddress = "0x8888888888888888888888888888888888888888"

        val event = accountingEventRepository.save(
            AccountingEvent(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                eventType = EventType.UNCLASSIFIED,
                classifierId = "seed",
                tokenAddress = existingAddress,
                tokenSymbol = "USDC",
                amountRaw = BigDecimal("1.0").toBigInteger(),
                amountDecimal = BigDecimal("1.0"),
                priceKrw = BigDecimal.ZERO,
                priceSource = PriceSource.UNKNOWN
            )
        )

        mockPrice(null, "ETH", LocalDate.of(2026, 2, 7), BigDecimal("4200000"))

        mockMvc.post("/api/unclassified/${event.id}/classify") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "eventType" to "INCOMING",
                    "tokenSymbol" to "eth",
                    "amountDecimal" to "1.0",
                    "tokenAddress" to null
                )
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("MANUAL_CLASSIFIED") }
            jsonPath("$.description") { value("Incoming ETH") }
        }

        val updatedEvent = accountingEventRepository.findById(event.id ?: error("event id should not be null"))
            ?: error("accounting event not found")
        kotlin.test.assertEquals(null, updatedEvent.tokenAddress)
        kotlin.test.assertEquals("ETH", updatedEvent.tokenSymbol)
        kotlin.test.assertEquals(0, updatedEvent.priceKrw?.compareTo(BigDecimal("4200000")))
        kotlin.test.assertEquals(PriceSource.COINGECKO, updatedEvent.priceSource)
    }

    @Test
    fun `manual classify rejects malformed token address`() {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x7878787878787878787878787878787878787878",
                    txHash = "0xmanual-classify-invalid-address",
                    blockNumber = 6,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-08T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()

        val event = accountingEventRepository.save(
            AccountingEvent(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                eventType = EventType.UNCLASSIFIED,
                classifierId = "seed",
                tokenSymbol = "USDC",
                amountRaw = BigDecimal("1.0").toBigInteger(),
                amountDecimal = BigDecimal("1.0"),
                priceKrw = BigDecimal.ZERO,
                priceSource = PriceSource.UNKNOWN
            )
        )

        mockMvc.post("/api/unclassified/${event.id}/classify") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "eventType" to "INCOMING",
                    "tokenSymbol" to "USDC",
                    "amountDecimal" to "1.0",
                    "tokenAddress" to "not-an-address"
                )
            )
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `manual classify rejects non eth token without address`() {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x7979797979797979797979797979797979797979",
                    txHash = "0xmanual-classify-no-address",
                    blockNumber = 7,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-09T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()

        val event = accountingEventRepository.save(
            AccountingEvent(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                eventType = EventType.UNCLASSIFIED,
                classifierId = "seed",
                tokenSymbol = "USDC",
                amountRaw = BigDecimal("1.0").toBigInteger(),
                amountDecimal = BigDecimal("1.0"),
                priceKrw = BigDecimal.ZERO,
                priceSource = PriceSource.UNKNOWN
            )
        )

        mockMvc.post("/api/unclassified/${event.id}/classify") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "eventType" to "INCOMING",
                    "tokenSymbol" to "USDC",
                    "amountDecimal" to "1.0",
                    "tokenAddress" to null
                )
            )
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `manual classify rejects swap event type`() {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x6969696969696969696969696969696969696969",
                    txHash = "0xmanual-classify-swap",
                    blockNumber = 9,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-11T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()
        val tokenAddress = "0xabababababababababababababababababababab"

        val event = accountingEventRepository.save(
            AccountingEvent(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                eventType = EventType.UNCLASSIFIED,
                classifierId = "seed",
                tokenSymbol = "USDC",
                amountRaw = BigDecimal("1.0").toBigInteger(),
                amountDecimal = BigDecimal("1.0"),
                priceKrw = BigDecimal.ZERO,
                priceSource = PriceSource.UNKNOWN
            )
        )

        mockMvc.post("/api/unclassified/${event.id}/classify") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "eventType" to "SWAP",
                    "tokenSymbol" to "USDC",
                    "amountDecimal" to "1.0",
                    "tokenAddress" to tokenAddress
                )
            )
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `manual classify canonicalizes symbol from token address`() {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x7070707070707070707070707070707070707070",
                    txHash = "0xmanual-classify-canonical-symbol",
                    blockNumber = 8,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-10T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()
        val tokenAddress = "0x1212121212121212121212121212121212121212"

        val event = accountingEventRepository.save(
            AccountingEvent(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                eventType = EventType.UNCLASSIFIED,
                classifierId = "seed",
                tokenSymbol = "WETH",
                amountRaw = BigDecimal("2.0").toBigInteger(),
                amountDecimal = BigDecimal("2.0"),
                priceKrw = BigDecimal.ZERO,
                priceSource = PriceSource.UNKNOWN
            )
        )

        whenever(blockchainDataPort.getTokenSymbol(eq(tokenAddress), anyOrNull())).thenReturn("USDC")
        mockPrice(tokenAddress, "USDC", LocalDate.of(2026, 2, 10), BigDecimal("1490"))

        mockMvc.post("/api/unclassified/${event.id}/classify") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "eventType" to "INCOMING",
                    "tokenSymbol" to "WETH",
                    "amountDecimal" to "2.0",
                    "tokenAddress" to tokenAddress
                )
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.description") { value("Incoming USDC") }
        }

        val updatedEvent = accountingEventRepository.findById(event.id ?: error("event id should not be null"))
            ?: error("accounting event not found")
        kotlin.test.assertEquals("USDC", updatedEvent.tokenSymbol)
        kotlin.test.assertEquals(tokenAddress, updatedEvent.tokenAddress)
        kotlin.test.assertEquals(0, updatedEvent.priceKrw?.compareTo(BigDecimal("1490")))
        kotlin.test.assertEquals(PriceSource.COINGECKO, updatedEvent.priceSource)
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

    private fun seedJournalEntryWithNonTokenOffset(status: JournalStatus): Long {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x4444444444444444444444444444444444444444",
                    txHash = "0xjournal-null-token-tx",
                    blockNumber = 2,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-04T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()

        val saved = journalRepository.save(
            JournalEntry(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                entryDate = Instant.parse("2026-02-04T00:00:00Z"),
                description = "mixed journal",
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
                        creditAmount = BigDecimal("1000")
                    )
                )
            )
        )

        return saved.id ?: error("journal id should not be null")
    }

    private fun seedAddressAwareJournalEntry(status: JournalStatus): Long {
        val raw = rawTransactionRepository.saveAll(
            listOf(
                RawTransaction(
                    walletAddress = "0x5555555555555555555555555555555555555555",
                    txHash = "0xjournal-address-aware-tx",
                    blockNumber = 3,
                    txIndex = 0,
                    blockTimestamp = Instant.parse("2026-02-05T00:00:00Z"),
                    rawData = objectMapper.createObjectNode().put("seed", true),
                    txStatus = 1
                )
            )
        ).first()
        ensureAccount("자산:암호화폐:ERC20:USDC@0x1111111111111111111111111111111111111111", "USDC 보유 자산 (111111)")

        val saved = journalRepository.save(
            JournalEntry(
                rawTransactionId = raw.id ?: error("raw tx id should not be null"),
                entryDate = Instant.parse("2026-02-05T00:00:00Z"),
                description = "address aware journal",
                status = status,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ERC20:USDC@0x1111111111111111111111111111111111111111",
                        debitAmount = BigDecimal("1000"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = "0x1111111111111111111111111111111111111111",
                        tokenQuantity = BigDecimal("0.25")
                    ),
                    JournalLine(
                        accountCode = "수익:에어드롭",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("1000"),
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = "0x1111111111111111111111111111111111111111",
                        tokenQuantity = BigDecimal("0.25")
                    )
                )
            )
        )

        return saved.id ?: error("journal id should not be null")
    }

    private fun ensureAccount(code: String, name: String) {
        jdbcTemplate.update(
            """
            INSERT INTO accounts (code, name, category, is_system)
            VALUES (?, ?, 'ASSET', true)
            ON CONFLICT (code) DO NOTHING
            """.trimIndent(),
            code,
            name
        )
    }

    private fun mockPrice(tokenAddress: String?, tokenSymbol: String, date: LocalDate, priceKrw: BigDecimal) {
        whenever(pricePort.getPrice(eq(tokenAddress), eq(tokenSymbol), eq(date))).thenReturn(
            PriceInfo(
                tokenAddress = tokenAddress,
                tokenSymbol = tokenSymbol,
                date = date,
                priceKrw = priceKrw,
                source = PriceSource.COINGECKO
            )
        )
    }
}
