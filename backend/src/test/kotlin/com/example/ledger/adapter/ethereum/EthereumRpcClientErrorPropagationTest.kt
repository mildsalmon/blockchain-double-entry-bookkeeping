package com.example.ledger.adapter.ethereum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EthereumRpcClientErrorPropagationTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    private fun buildClientWithResponse(responseBody: String): EthereumRpcClient {
        val requestBodyUriSpec = mockk<WebClient.RequestBodyUriSpec>(relaxed = true)
        val requestBodySpec = mockk<WebClient.RequestBodySpec>(relaxed = true)
        val requestHeadersSpec = mockk<WebClient.RequestHeadersSpec<*>>(relaxed = true)
        val responseSpec = mockk<WebClient.ResponseSpec>(relaxed = true)
        val webClient = mockk<WebClient>(relaxed = true)

        every { webClient.post() } returns requestBodyUriSpec
        every { requestBodyUriSpec.contentType(any()) } returns requestBodySpec
        every { requestBodySpec.bodyValue(any()) } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
        every { responseSpec.bodyToMono(String::class.java) } returns Mono.just(responseBody)

        val client = EthereumRpcClient("http://localhost:8545", 16 * 1024 * 1024, objectMapper)
        // Inject the mocked WebClient via reflection since the field is private
        val webClientField = EthereumRpcClient::class.java.getDeclaredField("webClient")
        webClientField.isAccessible = true
        webClientField.set(client, webClient)
        return client
    }

    // ---- getBlockNumber ----

    @Test
    fun `getBlockNumber throws EthereumRpcException when RPC error is present`() {
        val errorJson = """{"jsonrpc":"2.0","id":1,"error":{"code":-32603,"message":"Internal error"}}"""
        val client = buildClientWithResponse(errorJson)

        val ex = assertFailsWith<EthereumRpcException> {
            client.getBlockNumber()
        }
        assert(ex.code == -32603)
        assert(ex.rpcMessage == "Internal error")
    }

    @Test
    fun `getBlockNumber throws EthereumRpcException when result is null`() {
        val nullResultJson = """{"jsonrpc":"2.0","id":1,"result":null}"""
        val client = buildClientWithResponse(nullResultJson)

        assertFailsWith<EthereumRpcException> {
            client.getBlockNumber()
        }
    }

    @Test
    fun `getBlockNumber returns parsed block number on success`() {
        val successJson = """{"jsonrpc":"2.0","id":1,"result":"0x10"}"""
        val client = buildClientWithResponse(successJson)

        val result = client.getBlockNumber()
        assert(result == 16L)
    }

    // ---- getBlockByNumber ----

    @Test
    fun `getBlockByNumber throws EthereumRpcException when RPC error is present`() {
        val errorJson = """{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid params"}}"""
        val client = buildClientWithResponse(errorJson)

        val ex = assertFailsWith<EthereumRpcException> {
            client.getBlockByNumber(100L)
        }
        assert(ex.code == -32602)
        assert(ex.rpcMessage == "Invalid params")
    }

    @Test
    fun `getBlockByNumber returns null when result is null (block not yet mined)`() {
        val nullResultJson = """{"jsonrpc":"2.0","id":1,"result":null}"""
        val client = buildClientWithResponse(nullResultJson)

        val result = client.getBlockByNumber(999999999L)
        assertNull(result)
    }

    // ---- getTransactionReceipt ----

    @Test
    fun `getTransactionReceipt throws EthereumRpcException when RPC error is present`() {
        val errorJson = """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"execution reverted"}}"""
        val client = buildClientWithResponse(errorJson)

        val ex = assertFailsWith<EthereumRpcException> {
            client.getTransactionReceipt("0xabc")
        }
        assert(ex.code == -32000)
        assert(ex.rpcMessage == "execution reverted")
    }

    @Test
    fun `getTransactionReceipt returns null when result is null (pending tx)`() {
        val nullResultJson = """{"jsonrpc":"2.0","id":1,"result":null}"""
        val client = buildClientWithResponse(nullResultJson)

        val result = client.getTransactionReceipt("0xpending")
        assertNull(result)
    }

    // ---- getTransactionByHash ----

    @Test
    fun `getTransactionByHash throws EthereumRpcException when RPC error is present`() {
        val errorJson = """{"jsonrpc":"2.0","id":1,"error":{"code":-32001,"message":"resource not found"}}"""
        val client = buildClientWithResponse(errorJson)

        val ex = assertFailsWith<EthereumRpcException> {
            client.getTransactionByHash("0xabc")
        }
        assert(ex.code == -32001)
        assert(ex.rpcMessage == "resource not found")
    }

    @Test
    fun `getTransactionByHash returns null when result is null (pending tx)`() {
        val nullResultJson = """{"jsonrpc":"2.0","id":1,"result":null}"""
        val client = buildClientWithResponse(nullResultJson)

        val result = client.getTransactionByHash("0xpending")
        assertNull(result)
    }
}
