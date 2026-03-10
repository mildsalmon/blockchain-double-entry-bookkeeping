package com.example.ledger.integration

import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.RawTransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.nullValue
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.sql.Timestamp
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

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

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
            jsonPath("$.positions[0].chain") { value("ETHEREUM") }
            jsonPath("$.positions[0].chainLabel") { value("Ethereum") }
            jsonPath("$.positions[0].tokenAddress") { value(nullValue()) }
            jsonPath("$.positions[0].displayLabel") { value("ETH (Ethereum)") }
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
            jsonPath("$.positions[0].displayLabel") { value("WBTC (Ethereum)") }
            jsonPath("$.positions[1].accountCode") { value("자산:암호화폐:ETH") }
            jsonPath("$.positions[1].tokenSymbol") { value("USDC") }
            jsonPath("$.positions[1].displayLabel") { value("USDC (Ethereum)") }
        }
    }

    @Test
    fun `dashboard balances keeps cached symbol when read refresh fails`() {
        val walletAddress = "0x9999999999999999999999999999999999999999"
        val tokenAddress = "0x1111111111111111111111111111111111111111"
        val raw = seedRawTx(walletAddress, "0xdash-stale-cache", Instant.parse("2026-02-24T00:00:00Z"))
        ensureAccount("자산:암호화폐:ERC20:USDC@$tokenAddress", "USDC 보유 자산 (111111)")

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(raw.id),
                entryDate = raw.blockTimestamp,
                description = "Incoming USDC",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ERC20:USDC@$tokenAddress",
                        debitAmount = BigDecimal("500"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddress,
                        tokenQuantity = BigDecimal("10")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("500"),
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddress,
                        tokenQuantity = BigDecimal("10")
                    )
                )
            )
        )

        jdbcTemplate.update(
            """
            INSERT INTO token_metadata (chain, token_address, symbol, last_verified_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
            "ETHEREUM",
            tokenAddress,
            "USDC",
            Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        )

        whenever(blockchainDataPort.getTokenSymbol(eq(tokenAddress), anyOrNull())).thenThrow(RuntimeException("rpc down"))

        mockMvc.get("/api/dashboard/balances") {
            param("walletAddress", walletAddress)
        }.andExpect {
            status { isOk() }
            jsonPath("$.positions[0].tokenSymbol") { value("USDC") }
            jsonPath("$.positions[0].chain") { value("ETHEREUM") }
            jsonPath("$.positions[0].chainLabel") { value("Ethereum") }
            jsonPath("$.positions[0].tokenAddress") { value(tokenAddress) }
            jsonPath("$.positions[0].displayLabel") { value("USDC (Ethereum)") }
        }
    }

    @Test
    fun `dashboard balances prefers verified cached symbol over stale journal fallback when refresh fails`() {
        val walletAddress = "0x1212121212121212121212121212121212121212"
        val tokenAddress = "0x4444444444444444444444444444444444444444"
        val raw = seedRawTx(walletAddress, "0xdash-cache-precedence", Instant.parse("2026-02-28T00:00:00Z"))
        ensureAccount("자산:암호화폐:ERC20:USDC@$tokenAddress", "USDC 보유 자산 (444444)")

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(raw.id),
                entryDate = raw.blockTimestamp,
                description = "Incoming stale symbol",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ERC20:USDC@$tokenAddress",
                        debitAmount = BigDecimal("800"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "OLDUSDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddress,
                        tokenQuantity = BigDecimal("8")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("800"),
                        tokenSymbol = "OLDUSDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddress,
                        tokenQuantity = BigDecimal("8")
                    )
                )
            )
        )

        jdbcTemplate.update(
            """
            INSERT INTO token_metadata (chain, token_address, symbol, last_verified_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
            "ETHEREUM",
            tokenAddress,
            "USDC",
            Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        )

        whenever(blockchainDataPort.getTokenSymbol(eq(tokenAddress), anyOrNull())).thenThrow(RuntimeException("rpc down"))

        mockMvc.get("/api/dashboard/balances") {
            param("walletAddress", walletAddress)
        }.andExpect {
            status { isOk() }
            jsonPath("$.positions[0].tokenSymbol") { value("USDC") }
            jsonPath("$.positions[0].displayLabel") { value("USDC (Ethereum)") }
        }
    }

    @Test
    fun `dashboard balances uses fallback symbol without caching when rpc refresh fails and cache is missing`() {
        val walletAddress = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val tokenAddress = "0x3333333333333333333333333333333333333333"
        val raw = seedRawTx(walletAddress, "0xdash-fallback-only", Instant.parse("2026-02-27T00:00:00Z"))
        ensureAccount("자산:암호화폐:ERC20:USDC@$tokenAddress", "USDC 보유 자산 (333333)")

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(raw.id),
                entryDate = raw.blockTimestamp,
                description = "Incoming fallback USDC",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ERC20:USDC@$tokenAddress",
                        debitAmount = BigDecimal("700"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddress,
                        tokenQuantity = BigDecimal("7")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("700"),
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddress,
                        tokenQuantity = BigDecimal("7")
                    )
                )
            )
        )

        whenever(blockchainDataPort.getTokenSymbol(eq(tokenAddress), anyOrNull())).thenThrow(RuntimeException("rpc down"))

        mockMvc.get("/api/dashboard/balances") {
            param("walletAddress", walletAddress)
        }.andExpect {
            status { isOk() }
            jsonPath("$.positions[0].tokenSymbol") { value("USDC") }
            jsonPath("$.positions[0].displayLabel") { value("USDC (Ethereum)") }
        }

        val metadataCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM token_metadata WHERE chain = ? AND token_address = ?",
            Long::class.java,
            "ETHEREUM",
            tokenAddress
        ) ?: 0L
        kotlin.test.assertEquals(0L, metadataCount)
    }

    @Test
    fun `dashboard balances separates same symbol positions by token address`() {
        val walletAddress = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val tokenAddressA = "0x0000000000000000000000000000000000000001"
        val tokenAddressB = "0x0000000000000000000000000000000000000002"
        val rawA = seedRawTx(walletAddress, "0xdash-usdc-a", Instant.parse("2026-02-25T00:00:00Z"))
        val rawB = seedRawTx(walletAddress, "0xdash-usdc-b", Instant.parse("2026-02-26T00:00:00Z"))
        ensureAccount("자산:암호화폐:ERC20:USDC@$tokenAddressA", "USDC 보유 자산 (000001)")
        ensureAccount("자산:암호화폐:ERC20:USDC@$tokenAddressB", "USDC 보유 자산 (000002)")

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(rawA.id),
                entryDate = rawA.blockTimestamp,
                description = "Incoming USDC A",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ERC20:USDC@$tokenAddressA",
                        debitAmount = BigDecimal("100"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddressA,
                        tokenQuantity = BigDecimal("1")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("100"),
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddressA,
                        tokenQuantity = BigDecimal("1")
                    )
                )
            )
        )

        journalRepository.save(
            JournalEntry(
                rawTransactionId = requireNotNull(rawB.id),
                entryDate = rawB.blockTimestamp,
                description = "Incoming USDC B",
                status = JournalStatus.AUTO_CLASSIFIED,
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ERC20:USDC@$tokenAddressB",
                        debitAmount = BigDecimal("200"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddressB,
                        tokenQuantity = BigDecimal("2")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("200"),
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = tokenAddressB,
                        tokenQuantity = BigDecimal("2")
                    )
                )
            )
        )

        mockMvc.get("/api/dashboard/balances") {
            param("walletAddress", walletAddress)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.tokenCount") { value(2) }
            jsonPath("$.summary.positionCount") { value(2) }
            jsonPath("$.positions[0].tokenAddress") { value(tokenAddressA) }
            jsonPath("$.positions[1].tokenAddress") { value(tokenAddressB) }
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
}
