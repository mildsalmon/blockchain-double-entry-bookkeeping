package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.WalletRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CutoffWalletApiIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @MockBean
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @Test
    fun `can register cutoff wallet with tracked tokens`() {
        val address = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val payload = mapOf(
            "address" to address,
            "mode" to "BALANCE_FLOW_CUTOFF",
            "cutoffBlock" to 21_000_000,
            "trackedTokens" to listOf(
                "0xBbBBBBbBBbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBb",
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            )
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.address") { value(address) }
            jsonPath("$.mode") { value("BALANCE_FLOW_CUTOFF") }
            jsonPath("$.cutoffBlock") { value(21_000_000) }
        }

        val saved = walletRepository.findByAddress(address)
        assertNotNull(saved)
        assertEquals(WalletSyncMode.BALANCE_FLOW_CUTOFF, saved.syncMode)
        assertEquals(21_000_000L, saved.cutoffBlock)
        assertEquals(listOf("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), saved.trackedTokens)
        verify(syncPipelineUseCase).syncAsync(address)
    }

    @Test
    fun `cutoff mode accepts legacy startBlock alias`() {
        val address = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val payload = mapOf(
            "address" to address,
            "mode" to "BALANCE_FLOW_CUTOFF",
            "startBlock" to 20_000_100
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.cutoffBlock") { value(20_000_100) }
        }
    }

    @Test
    fun `cutoffBlock takes precedence when both cutoffBlock and startBlock are provided`() {
        val address = "0xcccccccccccccccccccccccccccccccccccccccc"
        val payload = mapOf(
            "address" to address,
            "mode" to "BALANCE_FLOW_CUTOFF",
            "cutoffBlock" to 20_000_200,
            "startBlock" to 20_000_000
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.cutoffBlock") { value(20_000_200) }
        }
    }

    @Test
    fun `cutoff mode requires cutoffBlock or startBlock`() {
        val payload = mapOf(
            "address" to "0xdddddddddddddddddddddddddddddddddddddddd",
            "mode" to "BALANCE_FLOW_CUTOFF"
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `cutoff config is immutable after snapshot completion`() {
        val address = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        walletRepository.save(
            Wallet(
                address = address,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_COMPLETED,
                syncStatus = SyncStatus.COMPLETED,
                cutoffBlock = 10_000L,
                snapshotBlock = 10_000L,
                deltaSyncedBlock = 10_000L,
                lastSyncedBlock = 10_000L,
                trackedTokens = listOf("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            )
        )

        val payload = mapOf(
            "address" to address,
            "mode" to "BALANCE_FLOW_CUTOFF",
            "cutoffBlock" to 10_001L,
            "trackedTokens" to listOf("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
        )

        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isConflict() }
        }

        val saved = walletRepository.findByAddress(address)
        assertNotNull(saved)
        assertEquals(10_000L, saved.cutoffBlock)
        assertEquals(listOf("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), saved.trackedTokens)
        verify(syncPipelineUseCase, never()).syncAsync(address)
    }
}
