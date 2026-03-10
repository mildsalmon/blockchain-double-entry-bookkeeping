package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.WalletRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WalletApiIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @MockBean
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @Test
    fun `can register wallet with optional start block`() {
        val address = "0x1111111111111111111111111111111111111111"
        val payload = mapOf(
            "address" to address,
            "label" to "client-a",
            "startBlock" to 19_000_000
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.address") { value(address) }
            jsonPath("$.lastSyncedBlock") { value(19_000_000) }
        }

        val saved = walletRepository.findByAddress(address)
        assertNotNull(saved)
        assertEquals(19_000_000L, saved.lastSyncedBlock)
        verify(syncPipelineUseCase).syncAsync(address)
    }

    @Test
    fun `rejects negative start block`() {
        val payload = mapOf(
            "address" to "0x2222222222222222222222222222222222222222",
            "startBlock" to -1
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `can retry wallet sync`() {
        val address = "0x3333333333333333333333333333333333333333"
        walletRepository.save(Wallet(address = address))

        mockMvc.post("/api/wallets/$address/retry") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.address") { value(address) }
        }

        verify(syncPipelineUseCase).syncAsync(address)
    }

    @Test
    fun `wallet list exposes cutoff signoff and token visibility`() {
        val walletAddress = "0xabababababababababababababababababababab"
        val seededToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedSuspectedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val discoveredToken = "0xcccccccccccccccccccccccccccccccccccccccc"

        whenever(blockchainDataPort.getTokenSymbol(seededToken, 100L)).thenReturn("USDC")
        whenever(blockchainDataPort.getTokenSymbol(omittedSuspectedToken, null)).thenReturn("UNI")
        whenever(blockchainDataPort.getTokenSymbol(discoveredToken, null)).thenReturn("LINK")

        val createPayload = mapOf(
            "address" to walletAddress,
            "reviewedBy" to "ops-kim",
            "preflightSummaryHash" to cutoffPreflightSummaryHash(walletAddress, 100, listOf(seededToken)),
            "mode" to "BALANCE_FLOW_CUTOFF",
            "cutoffBlock" to 100,
            "trackedTokens" to listOf(seededToken)
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(createPayload)
        }.andExpect {
            status { isCreated() }
        }

        clearInvocations(blockchainDataPort)

        val omittedRawTxId = jdbcTemplate.queryForObject(
            """
            INSERT INTO raw_transactions (wallet_address, tx_hash, block_number, tx_index, block_timestamp, raw_data, tx_status)
            VALUES (?, '0xwallet-omitted', 150, 0, NOW(), '{}'::jsonb, 1)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            walletAddress
        ) ?: error("raw tx id should exist")
        jdbcTemplate.update(
            """
            INSERT INTO accounting_events (
                raw_transaction_id,
                event_type,
                classifier_id,
                token_address,
                token_symbol,
                amount_raw,
                amount_decimal
            ) VALUES (?, 'INCOMING', 'seed', ?, 'UNI', 1000000000000000000, 1)
            """.trimIndent(),
            omittedRawTxId,
            omittedSuspectedToken
        )

        val discoveredRawTxId = jdbcTemplate.queryForObject(
            """
            INSERT INTO raw_transactions (wallet_address, tx_hash, block_number, tx_index, block_timestamp, raw_data, tx_status)
            VALUES (?, '0xwallet-discovered', 2_000, 0, NOW(), '{}'::jsonb, 1)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            walletAddress
        ) ?: error("raw tx id should exist")
        jdbcTemplate.update(
            """
            INSERT INTO accounting_events (
                raw_transaction_id,
                event_type,
                classifier_id,
                token_address,
                token_symbol,
                amount_raw,
                amount_decimal
            ) VALUES (?, 'INCOMING', 'seed', ?, 'LINK', 1000000000000000000, 1)
            """.trimIndent(),
            discoveredRawTxId,
            discoveredToken
        )

        mockMvc.get("/api/wallets")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].latestCutoffSignOff.reviewedBy") { value("ops-kim") }
                jsonPath("$[0].seededTokens.length()") { value(2) }
                jsonPath("$[0].discoveredTokens.length()") { value(2) }
                jsonPath("$[0].omittedSuspectedTokens.length()") { value(1) }
                jsonPath("$[0].omittedSuspectedTokens[0].tokenAddress") { value(omittedSuspectedToken) }
            }

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM token_metadata", Int::class.java))
        verifyNoInteractions(blockchainDataPort)
    }

    @Test
    fun `re-registering existing wallet with config override is rejected`() {
        val address = "0xdededededededededededededededededededede"
        walletRepository.save(
            Wallet(
                address = address,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                cutoffBlock = 100L,
                trackedTokens = listOf("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            )
        )

        val payload = mapOf(
            "address" to address,
            "reviewedBy" to "ops-new",
            "preflightSummaryHash" to cutoffPreflightSummaryHash(address, 101, listOf("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")),
            "mode" to "BALANCE_FLOW_CUTOFF",
            "cutoffBlock" to 101,
            "trackedTokens" to listOf("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `re-registering existing cutoff wallet without config retries sync`() {
        val address = "0xcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd"
        walletRepository.save(
            Wallet(
                address = address,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                cutoffBlock = 100L,
                trackedTokens = listOf("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            )
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("address" to address))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.address") { value(address) }
            jsonPath("$.mode") { value("BALANCE_FLOW_CUTOFF") }
            jsonPath("$.cutoffBlock") { value(100) }
        }

        verify(syncPipelineUseCase).syncAsync(address)
    }

    @Test
    fun `can delete wallet and related ledger data`() {
        val address = "0x4444444444444444444444444444444444444444"
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        walletRepository.save(
            Wallet(
                address = address,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_COMPLETED,
                cutoffBlock = 100L,
                snapshotBlock = 100L,
                deltaSyncedBlock = 100L,
                trackedTokens = listOf(trackedToken)
            )
        )
        val wallet = walletRepository.findByAddress(address)
        val walletId = requireNotNull(wallet?.id)

        jdbcTemplate.update(
            """
            INSERT INTO wallet_balance_snapshots (wallet_id, token_address, token_symbol, balance_raw, cutoff_block)
            VALUES (?, ?, 'ETH', 123, 100)
            """.trimIndent(),
            walletId,
            trackedToken
        )

        val rawTxId = jdbcTemplate.queryForObject(
            """
            INSERT INTO raw_transactions (wallet_address, tx_hash, block_number, tx_index, block_timestamp, raw_data, tx_status)
            VALUES (?, '0xwallet-delete-seed', 100, 0, NOW(), '{}'::jsonb, 1)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            address
        ) ?: error("raw tx id should exist")

        val eventId = jdbcTemplate.queryForObject(
            """
            INSERT INTO accounting_events (
                raw_transaction_id,
                event_type,
                classifier_id,
                token_address,
                token_symbol,
                amount_raw,
                amount_decimal
            ) VALUES (?, 'TRANSFER', 'seed', ?, 'ETH', 1000000000000000000, 1)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            rawTxId,
            trackedToken
        ) ?: error("event id should exist")

        val journalEntryId = jdbcTemplate.queryForObject(
            """
            INSERT INTO journal_entries (accounting_event_id, raw_transaction_id, entry_date, description, status)
            VALUES (?, ?, NOW(), 'seed', 'REVIEW_REQUIRED')
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            eventId,
            rawTxId
        ) ?: error("journal id should exist")

        jdbcTemplate.update(
            """
            INSERT INTO journal_lines (journal_entry_id, account_code, debit_amount, credit_amount, token_symbol, token_quantity)
            VALUES (?, '자산:암호화폐:ETH', 1, 0, 'ETH', 1)
            """.trimIndent(),
            journalEntryId
        )

        jdbcTemplate.update(
            """
            INSERT INTO cost_basis_lots (
                wallet_address,
                token_symbol,
                acquisition_date,
                quantity,
                remaining_qty,
                unit_cost_krw,
                raw_transaction_id
            ) VALUES (?, 'ETH', NOW(), 1, 1, 1000, ?)
            """.trimIndent(),
            address,
            rawTxId
        )

        mockMvc.delete("/api/wallets/$address")
            .andExpect {
                status { isNoContent() }
            }

        assertEquals(null, walletRepository.findByAddress(address))
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_transactions WHERE wallet_address = ?", Int::class.java, address)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounting_events WHERE raw_transaction_id = ?", Int::class.java, rawTxId)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE raw_transaction_id = ?", Int::class.java, rawTxId)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cost_basis_lots WHERE wallet_address = ?", Int::class.java, address)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wallet_tracked_tokens WHERE wallet_id = ?", Int::class.java, walletId)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wallet_balance_snapshots WHERE wallet_id = ?", Int::class.java, walletId)
        )
    }

    private fun cutoffPreflightSummaryHash(
        address: String,
        cutoffBlock: Long,
        trackedTokens: List<String> = emptyList()
    ): String {
        val response = mockMvc.post("/api/wallets/cutoff-preflight") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "address" to address,
                    "cutoffBlock" to cutoffBlock,
                    "trackedTokens" to trackedTokens
                )
            )
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        return objectMapper.readTree(response).path("summaryHash").asText()
    }
}
