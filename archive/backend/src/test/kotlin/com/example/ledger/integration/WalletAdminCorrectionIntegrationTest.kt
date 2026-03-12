package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.application.usecase.PendingWalletSyncRecovery
import com.example.ledger.application.usecase.WalletAdminCorrectionUseCase
import com.example.ledger.application.usecase.WalletCutoffInsightsService
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.WalletRepository
import com.example.ledger.domain.service.AuditService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalletAdminCorrectionIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var walletCutoffInsightsService: WalletCutoffInsightsService

    @Autowired
    private lateinit var auditService: AuditService

    @Autowired
    private lateinit var walletAdminCorrectionUseCase: WalletAdminCorrectionUseCase

    @Autowired
    private lateinit var pendingWalletSyncRecovery: PendingWalletSyncRecovery

    @MockBean
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @Test
    fun `admin correction preflight returns snapshot restate preview with omitted match`() {
        val address = "0x7777777777777777777777777777777777777777"
        val cutoffBlock = 100L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        val wallet = createEligibleCutoffWallet(address, cutoffBlock, listOf(trackedToken))
        insertExistingCutoffLedgerData(address, requireNotNull(wallet.id), trackedToken, cutoffBlock)
        insertOmittedCandidate(address, omittedToken, 150L)
        whenever(blockchainDataPort.getTokenSymbol(eq(omittedToken), eq(cutoffBlock))).thenReturn("UNI")

        mockMvc.post("/api/wallets/$address/admin-corrections/preflight") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-100",
                    "reason" to "missed cutoff token"
                )
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.walletAddress") { value(address) }
            jsonPath("$.strategy") { value("SNAPSHOT_RESTATE") }
            jsonPath("$.currentTrackedTokens.length()") { value(1) }
            jsonPath("$.resultingTrackedTokens.length()") { value(2) }
            jsonPath("$.requestedTokens[0]") { value(omittedToken) }
            jsonPath("$.omittedCandidateMatches.length()") { value(1) }
            jsonPath("$.omittedCandidateMatches[0].tokenAddress") { value(omittedToken) }
            jsonPath("$.currentSeededTokens.length()") { value(2) }
            jsonPath("$.resultingSeededTokens.length()") { value(3) }
            jsonPath("$.impact.snapshotCount") { value(1) }
            jsonPath("$.impact.rawTransactionCount") { value(2) }
            jsonPath("$.impact.accountingEventCount") { value(2) }
            jsonPath("$.impact.journalEntryCount") { value(1) }
            jsonPath("$.impact.costBasisLotCount") { value(1) }
            jsonPath("$.impact.replayFromBlock") { value(100) }
            jsonPath("$.impact.replayToBlock") { value(150) }
            jsonPath("$.impact.replayBlockSpan") { value(50) }
            jsonPath("$.summaryHash") { exists() }
            jsonPath("$.warnings.length()") { value(1) }
        }

        assertNotNull(walletRepository.findByAddress(wallet.address))
    }

    @Test
    fun `admin correction apply purges cutoff ledger data resets wallet and triggers resync`() {
        val address = "0x8888888888888888888888888888888888888888"
        val cutoffBlock = 200L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        val wallet = createEligibleCutoffWallet(address, cutoffBlock, listOf(trackedToken))
        val walletId = requireNotNull(wallet.id)
        insertExistingCutoffLedgerData(address, walletId, trackedToken, cutoffBlock)
        insertOmittedCandidate(address, omittedToken, cutoffBlock + 10)
        whenever(blockchainDataPort.getTokenSymbol(eq(omittedToken), eq(cutoffBlock))).thenReturn("UNI")

        val preflightResponse = mockMvc.post("/api/wallets/$address/admin-corrections/preflight") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-200",
                    "reason" to "late discovery after close review"
                )
            )
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val summaryHash = objectMapper.readTree(preflightResponse).path("summaryHash").asText()

        mockMvc.post("/api/wallets/$address/admin-corrections/apply") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-200",
                    "reason" to "late discovery after close review",
                    "summaryHash" to summaryHash
                )
            )
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.address") { value(address) }
            jsonPath("$.syncStatus") { value("PENDING") }
            jsonPath("$.syncPhase") { value("SNAPSHOT_PENDING") }
            jsonPath("$.snapshotBlock") { isEmpty() }
            jsonPath("$.deltaSyncedBlock") { isEmpty() }
            jsonPath("$.trackedTokens.length()") { value(2) }
            jsonPath("$.adminCorrectionEnabled") { value(true) }
            jsonPath("$.latestCutoffSignOff.source") { value("ADMIN_CORRECTION") }
            jsonPath("$.latestCutoffSignOff.approvalReference") { value("APR-200") }
            jsonPath("$.latestCutoffSignOff.reason") { value("late discovery after close review") }
        }

        val updated = walletRepository.findByAddress(address)
        assertNotNull(updated)
        assertEquals(SyncStatus.PENDING, updated.syncStatus)
        assertEquals(WalletSyncPhase.SNAPSHOT_PENDING, updated.syncPhase)
        assertEquals(null, updated.snapshotBlock)
        assertEquals(null, updated.deltaSyncedBlock)
        assertEquals(cutoffBlock, updated.lastSyncedBlock)
        assertEquals(listOf(trackedToken, omittedToken), updated.trackedTokens)

        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_transactions WHERE wallet_address = ?", Int::class.java, address)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounting_events", Int::class.java)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM journal_entries", Int::class.java)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cost_basis_lots WHERE wallet_address = ?", Int::class.java, address)
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wallet_balance_snapshots WHERE wallet_id = ?", Int::class.java, walletId)
        )
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_log
                WHERE entity_type = 'WALLET_CUTOFF_ADMIN_CORRECTION'
                  AND entity_id = ?
                  AND action = 'APPLY_SNAPSHOT_RESTATE'
                """.trimIndent(),
                Int::class.java,
                address
            )
        )
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_log
                WHERE entity_type = 'WALLET_CUTOFF_SEED_SIGNOFF'
                  AND entity_id = ?
                  AND action = 'APPROVE_SEED'
                """.trimIndent(),
                Int::class.java,
                address
            )
        )
        assertEquals(
            "ops-kim",
            jdbcTemplate.queryForObject(
                """
                SELECT actor FROM audit_log
                WHERE entity_type = 'WALLET_CUTOFF_ADMIN_CORRECTION'
                  AND entity_id = ?
                  AND action = 'APPLY_SNAPSHOT_RESTATE'
                ORDER BY created_at DESC
                LIMIT 1
                """.trimIndent(),
                String::class.java,
                address
            )
        )
        assertEquals(
            "ops-kim",
            jdbcTemplate.queryForObject(
                """
                SELECT actor FROM audit_log
                WHERE entity_type = 'WALLET_CUTOFF_SEED_SIGNOFF'
                  AND entity_id = ?
                  AND action = 'APPROVE_SEED'
                ORDER BY created_at DESC
                LIMIT 1
                """.trimIndent(),
                String::class.java,
                address
            )
        )

        verify(syncPipelineUseCase).syncAsync(address)
    }

    @Test
    fun `admin correction apply falls back to inline sync when async dispatch fails`() {
        val address = "0x1717171717171717171717171717171717171717"
        val cutoffBlock = 210L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        createEligibleCutoffWallet(address, cutoffBlock, listOf(trackedToken))
        insertExistingCutoffLedgerData(address, requireNotNull(walletRepository.findByAddress(address)?.id), trackedToken, cutoffBlock)
        insertOmittedCandidate(address, omittedToken, cutoffBlock + 10)
        whenever(blockchainDataPort.getTokenSymbol(eq(omittedToken), eq(cutoffBlock))).thenReturn("UNI")
        doThrow(RuntimeException("queue unavailable")).whenever(syncPipelineUseCase).syncAsync(address)

        val preflight = walletAdminCorrectionUseCase.preflight(
            address = address,
            tokenAddresses = listOf(omittedToken),
            actor = "ops-kim",
            approvalReference = "APR-210",
            reason = "dispatch fallback test"
        )

        val response = walletAdminCorrectionUseCase.apply(
            address = address,
            tokenAddresses = listOf(omittedToken),
            actor = "ops-kim",
            approvalReference = "APR-210",
            reason = "dispatch fallback test",
            summaryHash = preflight.summaryHash
        )

        assertEquals(SyncStatus.PENDING, response.syncStatus)
        verify(syncPipelineUseCase).syncAsync(address)
        verify(syncPipelineUseCase).sync(address)
    }

    @Test
    fun `admin correction endpoints reject requests without admin credentials`() {
        val address = "0x9999999999999999999999999999999999999999"
        val cutoffBlock = 300L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        createEligibleCutoffWallet(address, cutoffBlock, listOf(trackedToken))
        insertOmittedCandidate(address, omittedToken, cutoffBlock + 5)

        mockMvc.post("/api/wallets/$address/admin-corrections/preflight") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-300",
                    "reason" to "missing admin auth"
                )
            )
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `admin correction session endpoint returns authenticated principal`() {
        mockMvc.get("/api/admin-corrections/session") {
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
        }.andExpect {
            status { isOk() }
            jsonPath("$.authenticated") { value(true) }
            jsonPath("$.username") { value("ops-kim") }
        }
    }

    @Test
    fun `admin correction endpoints reject wrong admin credentials`() {
        val address = "0x1111111111111111111111111111111111111111"
        val cutoffBlock = 301L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        createEligibleCutoffWallet(address, cutoffBlock, listOf(trackedToken))
        insertOmittedCandidate(address, omittedToken, cutoffBlock + 5)

        mockMvc.post("/api/wallets/$address/admin-corrections/preflight") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader(password = "wrong-password"))
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-301",
                    "reason" to "wrong password"
                )
            )
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `admin correction apply rejects sync in progress wallet`() {
        val address = "0x1212121212121212121212121212121212121212"
        val cutoffBlock = 400L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        createEligibleCutoffWallet(
            address = address,
            cutoffBlock = cutoffBlock,
            trackedTokens = listOf(trackedToken),
            syncStatus = SyncStatus.SYNCING
        )
        insertOmittedCandidate(address, omittedToken, cutoffBlock + 1)

        mockMvc.post("/api/wallets/$address/admin-corrections/preflight") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-400",
                    "reason" to "wallet is syncing"
                )
            )
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `admin correction requires existing cutoff sign-off baseline`() {
        val address = "0x1616161616161616161616161616161616161616"
        val cutoffBlock = 405L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        walletRepository.save(
            Wallet(
                address = address,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.DELTA_COMPLETED,
                syncStatus = SyncStatus.COMPLETED,
                cutoffBlock = cutoffBlock,
                snapshotBlock = cutoffBlock,
                deltaSyncedBlock = cutoffBlock + 5,
                lastSyncedBlock = cutoffBlock + 5,
                trackedTokens = listOf(trackedToken)
            )
        )
        insertOmittedCandidate(address, omittedToken, cutoffBlock + 1)

        mockMvc.post("/api/wallets/$address/admin-corrections/preflight") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-405",
                    "reason" to "missing audited baseline"
                )
            )
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") { value("Admin correction requires an existing cutoff sign-off baseline.") }
        }
    }

    @Test
    fun `admin correction apply rejects stale summary after wallet state changes`() {
        val address = "0x1313131313131313131313131313131313131313"
        val cutoffBlock = 500L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedToken = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val extraToken = "0xcccccccccccccccccccccccccccccccccccccccc"

        val wallet = createEligibleCutoffWallet(address, cutoffBlock, listOf(trackedToken))
        insertOmittedCandidate(address, omittedToken, cutoffBlock + 2)

        val preflightResponse = mockMvc.post("/api/wallets/$address/admin-corrections/preflight") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-500",
                    "reason" to "stale preview"
                )
            )
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val summaryHash = objectMapper.readTree(preflightResponse).path("summaryHash").asText()

        walletRepository.save(wallet.copy(trackedTokens = listOf(trackedToken, extraToken)))

        mockMvc.post("/api/wallets/$address/admin-corrections/apply") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to listOf(omittedToken),
                    "approvalReference" to "APR-500",
                    "reason" to "stale preview",
                    "summaryHash" to summaryHash
                )
            )
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `admin correction rejects oversized payloads`() {
        val address = "0x1515151515151515151515151515151515151515"
        val cutoffBlock = 700L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        createEligibleCutoffWallet(address, cutoffBlock, listOf(trackedToken))

        mockMvc.post("/api/wallets/$address/admin-corrections/preflight") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.AUTHORIZATION, adminAuthHeader())
            content = objectMapper.writeValueAsString(
                mapOf(
                    "tokenAddresses" to List(21) { "0x${it.toString().padStart(40, 'a')}" },
                    "approvalReference" to "APR-${"9".repeat(140)}",
                    "reason" to "x".repeat(600)
                )
            )
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `concurrent admin correction apply allows only one stale preflight to win`() {
        val address = "0x1414141414141414141414141414141414141414"
        val cutoffBlock = 600L
        val trackedToken = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val omittedTokenA = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val omittedTokenB = "0xcccccccccccccccccccccccccccccccccccccccc"

        createEligibleCutoffWallet(address, cutoffBlock, listOf(trackedToken))
        insertOmittedCandidate(address, omittedTokenA, cutoffBlock + 10)
        insertOmittedCandidate(address, omittedTokenB, cutoffBlock + 11)

        val preflightA = walletAdminCorrectionUseCase.preflight(
            address = address,
            tokenAddresses = listOf(omittedTokenA),
            actor = "ops-kim",
            approvalReference = "APR-600-A",
            reason = "concurrent apply A"
        )
        val preflightB = walletAdminCorrectionUseCase.preflight(
            address = address,
            tokenAddresses = listOf(omittedTokenB),
            actor = "ops-kim",
            approvalReference = "APR-600-B",
            reason = "concurrent apply B"
        )

        val executor = Executors.newFixedThreadPool(2)
        val barrier = CyclicBarrier(2)
        try {
            val futureA = executor.submit(Callable {
                barrier.await(5, TimeUnit.SECONDS)
                runCatching {
                    walletAdminCorrectionUseCase.apply(
                        address = address,
                        tokenAddresses = listOf(omittedTokenA),
                        actor = "ops-kim",
                        approvalReference = "APR-600-A",
                        reason = "concurrent apply A",
                        summaryHash = preflightA.summaryHash
                    )
                }
            })
            val futureB = executor.submit(Callable {
                barrier.await(5, TimeUnit.SECONDS)
                runCatching {
                    walletAdminCorrectionUseCase.apply(
                        address = address,
                        tokenAddresses = listOf(omittedTokenB),
                        actor = "ops-kim",
                        approvalReference = "APR-600-B",
                        reason = "concurrent apply B",
                        summaryHash = preflightB.summaryHash
                    )
                }
            })

            val resultA = futureA.get(10, TimeUnit.SECONDS)
            val resultB = futureB.get(10, TimeUnit.SECONDS)

            assertTrue(resultA.isSuccess.xor(resultB.isSuccess))

            val failure = listOf(resultA, resultB).single { it.isFailure }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure is IllegalArgumentException || failure is com.example.ledger.application.exception.ConflictException)

            val updated = walletRepository.findByAddress(address)
            assertNotNull(updated)
            assertEquals(2, updated.trackedTokens.size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `pending wallet recovery re-dispatches admin correction replay`() {
        val address = "0x1818181818181818181818181818181818181818"
        createEligibleCutoffWallet(address, 800L, listOf("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        val pending = requireNotNull(walletRepository.findByAddress(address)).copy(
            syncStatus = SyncStatus.PENDING,
            syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
            snapshotBlock = null
        )
        walletRepository.save(pending)

        pendingWalletSyncRecovery.recoverPendingWallets("test")

        verify(syncPipelineUseCase).syncAsync(address)
    }

    @Test
    fun `pending wallet recovery falls back to inline sync when async dispatch fails`() {
        val address = "0x1919191919191919191919191919191919191919"
        createEligibleCutoffWallet(address, 810L, listOf("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        val pending = requireNotNull(walletRepository.findByAddress(address)).copy(
            syncStatus = SyncStatus.PENDING,
            syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
            snapshotBlock = null
        )
        walletRepository.save(pending)
        doThrow(RuntimeException("queue unavailable")).whenever(syncPipelineUseCase).syncAsync(address)

        pendingWalletSyncRecovery.recoverPendingWallets("test")

        verify(syncPipelineUseCase).syncAsync(address)
        verify(syncPipelineUseCase).sync(address)
    }

    private fun createEligibleCutoffWallet(
        address: String,
        cutoffBlock: Long,
        trackedTokens: List<String>,
        syncStatus: SyncStatus = SyncStatus.COMPLETED
    ): Wallet {
        val wallet = walletRepository.save(
            Wallet(
                address = address,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.DELTA_COMPLETED,
                syncStatus = syncStatus,
                cutoffBlock = cutoffBlock,
                snapshotBlock = cutoffBlock,
                deltaSyncedBlock = cutoffBlock + 50,
                lastSyncedBlock = cutoffBlock + 50,
                trackedTokens = trackedTokens
            )
        )

        auditService.log(
            entityType = WalletCutoffInsightsService.SIGN_OFF_ENTITY_TYPE,
            entityId = address,
            action = WalletCutoffInsightsService.SIGN_OFF_ACTION,
            oldValue = null,
            newValue = mapOf(
                "cutoffBlock" to cutoffBlock,
                "trackedTokens" to trackedTokens,
                "seededTokens" to listOf(
                    mapOf(
                        "tokenAddress" to null,
                        "tokenSymbol" to "ETH",
                        "displayLabel" to "ETH (Ethereum)"
                    ),
                    mapOf(
                        "tokenAddress" to trackedTokens.first(),
                        "tokenSymbol" to "ERR",
                        "displayLabel" to "ERR (Ethereum)"
                    )
                ),
                "seededTokenCount" to 2,
                "summaryHash" to walletCutoffInsightsService.buildSignOffSummaryHash(address, cutoffBlock, trackedTokens)
            ),
            actor = "ops-seed"
        )

        return wallet
    }

    private fun insertOmittedCandidate(address: String, tokenAddress: String, blockNumber: Long) {
        val rawTxId = jdbcTemplate.queryForObject(
            """
            INSERT INTO raw_transactions (wallet_address, tx_hash, block_number, tx_index, block_timestamp, raw_data, tx_status)
            VALUES (?, ?, ?, 0, NOW(), '{}'::jsonb, 1)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            address,
            "0xomitted-$blockNumber-$tokenAddress",
            blockNumber
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
            rawTxId,
            tokenAddress
        )
    }

    private fun insertExistingCutoffLedgerData(address: String, walletId: Long, trackedToken: String, cutoffBlock: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO wallet_balance_snapshots (wallet_id, token_address, token_symbol, balance_raw, cutoff_block)
            VALUES (?, ?, 'ERR', 123, ?)
            """.trimIndent(),
            walletId,
            trackedToken,
            cutoffBlock
        )

        val rawTxId = jdbcTemplate.queryForObject(
            """
            INSERT INTO raw_transactions (wallet_address, tx_hash, block_number, tx_index, block_timestamp, raw_data, tx_status)
            VALUES (?, '0xwallet-admin-correction-seed', ?, 0, NOW(), '{}'::jsonb, 1)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            address,
            cutoffBlock
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
            ) VALUES (?, 'TRANSFER', 'seed', ?, 'ERR', 1000000000000000000, 1)
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
    }

    private fun adminAuthHeader(
        username: String = "ops-kim",
        password: String = "test-admin-password"
    ): String {
        val raw = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }
}
