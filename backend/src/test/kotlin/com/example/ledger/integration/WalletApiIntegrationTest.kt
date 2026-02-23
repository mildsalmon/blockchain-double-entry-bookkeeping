package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.port.WalletRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
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
}

