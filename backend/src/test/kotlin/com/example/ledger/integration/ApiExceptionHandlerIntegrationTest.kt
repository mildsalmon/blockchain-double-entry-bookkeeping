package com.example.ledger.integration

import com.example.ledger.adapter.ethereum.EthereumRpcException
import com.example.ledger.application.usecase.IngestWalletUseCase
import com.example.ledger.application.usecase.JournalUseCase
import com.example.ledger.domain.port.PricePort
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.LocalDate

class ApiExceptionHandlerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var ingestWalletUseCase: IngestWalletUseCase

    @MockBean
    private lateinit var journalUseCase: JournalUseCase

    @MockBean
    private lateinit var pricePort: PricePort

    @Test
    fun `ethereum rpc exception is translated to 502 safe json`() {
        whenever(pricePort.getPrice(anyOrNull(), any(), any())).thenThrow(
            EthereumRpcException(-32000, "rpc unavailable")
        )

        mockMvc.get("/api/prices/ETH/2026-02-01")
            .andExpect {
                status { isBadGateway() }
                jsonPath("$.error") { value("Blockchain RPC error") }
                jsonPath("$.detail") { value("rpc unavailable") }
            }
    }

    @Test
    fun `webclient exception status is passed through with safe body`() {
        val ex = WebClientResponseException.create(
            503,
            "Service Unavailable",
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
            null
        )
        whenever(pricePort.getPrice(anyOrNull(), any(), any())).thenThrow(ex)

        mockMvc.get("/api/prices/ETH/2026-02-01")
            .andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.error") { value("Upstream API error") }
                jsonPath("$.detail") { value("HTTP 503") }
            }
    }

    @Test
    fun `data integrity violation is translated to 409`() {
        whenever(ingestWalletUseCase.registerWallet(any(), anyOrNull(), anyOrNull())).thenThrow(
            DataIntegrityViolationException("duplicate key value violates unique constraint")
        )

        val payload = mapOf("address" to "0x1111111111111111111111111111111111111111")
        mockMvc.post("/api/wallets") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(payload)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("Data integrity violation") }
        }
    }

    @Test
    fun `no such element is translated to 404`() {
        whenever(journalUseCase.get(any())).thenThrow(NoSuchElementException("Journal not found"))

        mockMvc.get("/api/journals/404")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.error") { value("Not found") }
                jsonPath("$.detail") { value("Journal not found") }
            }
    }

    @Test
    fun `unhandled exception returns 500 without stacktrace exposure`() {
        whenever(journalUseCase.list(any())).thenThrow(RuntimeException("boom"))

        mockMvc.get("/api/journals")
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.error") { value("Internal server error") }
                content { string(not(containsString("RuntimeException"))) }
                content { string(not(containsString("boom"))) }
                content { string(not(containsString("stackTrace"))) }
            }
    }

    @Test
    fun `existing illegal argument handling remains 400`() {
        whenever(ingestWalletUseCase.getStatus(any())).thenThrow(IllegalArgumentException("Wallet not found"))

        mockMvc.get("/api/wallets/0x1111111111111111111111111111111111111111/status")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("Wallet not found") }
            }
    }
}
