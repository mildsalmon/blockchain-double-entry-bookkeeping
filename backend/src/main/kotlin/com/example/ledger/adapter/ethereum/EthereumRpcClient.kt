package com.example.ledger.adapter.ethereum

import com.example.ledger.adapter.ethereum.dto.BlockResponse
import com.example.ledger.adapter.ethereum.dto.LogEntry
import com.example.ledger.adapter.ethereum.dto.RpcResponse
import com.example.ledger.adapter.ethereum.dto.TransactionReceiptResponse
import com.example.ledger.adapter.ethereum.dto.TransactionResponse
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigInteger

@Component
class EthereumRpcClient(
    @Value("\${app.ethereum.rpc-url}") rpcUrl: String,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EthereumRpcClient::class.java)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(rpcUrl.trimEnd('/'))
        .build()

    fun getLogs(fromBlock: Long, toBlock: Long, topics: List<String?>): List<LogEntry> {
        val params = mapOf(
            "fromBlock" to fromBlock.toHex(),
            "toBlock" to toBlock.toHex(),
            "topics" to topics
        )
        val payload = buildRequest("eth_getLogs", listOf(params))

        val responseBody = post(payload) ?: return emptyList()
        val response: RpcResponse<List<LogEntry>> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<List<LogEntry>>>() {}
        )
        response.error?.let {
            throw EthereumRpcException(it.code, it.message)
        }
        return response.result ?: emptyList()
    }

    fun getBlockByNumber(blockNumber: Long, fullTransactions: Boolean = true): BlockResponse? {
        val payload = buildRequest("eth_getBlockByNumber", listOf(blockNumber.toHex(), fullTransactions))

        val responseBody = post(payload) ?: return null
        val response: RpcResponse<BlockResponse> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<BlockResponse>>() {}
        )
        response.error?.let {
            throw EthereumRpcException(it.code, it.message)
        }
        return response.result
    }

    fun getTransactionReceipt(txHash: String): TransactionReceiptResponse? {
        val payload = buildRequest("eth_getTransactionReceipt", listOf(txHash))

        val responseBody = post(payload) ?: return null
        val response: RpcResponse<TransactionReceiptResponse> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<TransactionReceiptResponse>>() {}
        )
        response.error?.let {
            throw EthereumRpcException(it.code, it.message)
        }
        return response.result
    }

    fun getTransactionByHash(txHash: String): TransactionResponse? {
        val payload = buildRequest("eth_getTransactionByHash", listOf(txHash))

        val responseBody = post(payload) ?: return null
        val response: RpcResponse<TransactionResponse> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<TransactionResponse>>() {}
        )
        response.error?.let {
            throw EthereumRpcException(it.code, it.message)
        }
        return response.result
    }

    fun getBlockNumber(): Long {
        val payload = buildRequest("eth_blockNumber", emptyList<Any>())

        val responseBody = post(payload) ?: return 0L
        val response: RpcResponse<String> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<String>>() {}
        )
        response.error?.let {
            throw EthereumRpcException(it.code, it.message)
        }
        return response.result?.hexToLong()
            ?: throw EthereumRpcException(-1, "eth_blockNumber returned null result")
    }

    fun getNativeBalanceAtBlock(walletAddress: String, blockNumber: Long): BigInteger {
        val payload = buildRequest("eth_getBalance", listOf(walletAddress, blockNumber.toHex()))

        val responseBody = post(payload) ?: return BigInteger.ZERO
        val response: RpcResponse<String> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<String>>() {}
        )
        response.error?.let {
            log.warn("eth_getBalance RPC error {}: {}", it.code, it.message)
            return BigInteger.ZERO
        }
        return response.result?.hexToBigInteger() ?: BigInteger.ZERO
    }

    fun getTokenBalanceAtBlock(walletAddress: String, tokenAddress: String, blockNumber: Long): BigInteger {
        val callObject = mapOf(
            "to" to tokenAddress,
            "data" to encodeBalanceOf(walletAddress)
        )
        val payload = buildRequest("eth_call", listOf(callObject, blockNumber.toHex()))

        val responseBody = post(payload) ?: return BigInteger.ZERO
        val response: RpcResponse<String> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<String>>() {}
        )
        response.error?.let {
            log.warn("eth_call(balanceOf) RPC error {}: {}", it.code, it.message)
            return BigInteger.ZERO
        }
        return response.result?.hexToBigInteger() ?: BigInteger.ZERO
    }

    private fun buildRequest(method: String, params: List<Any?>): Map<String, Any?> = mapOf(
        "jsonrpc" to "2.0",
        "method" to method,
        "params" to params,
        "id" to 1
    )

    private fun post(payload: Map<String, Any?>): String? {
        return webClient.post()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(payload))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }
}

private fun Long.toHex(): String = "0x${toString(16)}"
private fun String.hexToLong(): Long = removePrefix("0x").ifEmpty { "0" }.toLong(16)
private fun String.hexToBigInteger(): BigInteger = BigInteger(removePrefix("0x").ifEmpty { "0" }, 16)
private fun encodeBalanceOf(walletAddress: String): String {
    val cleanAddress = walletAddress.removePrefix("0x").lowercase().padStart(64, '0')
    return "0x70a08231$cleanAddress"
}

class EthereumRpcException(
    val code: Int,
    val rpcMessage: String
) : RuntimeException("Ethereum RPC error(code=$code): $rpcMessage")
