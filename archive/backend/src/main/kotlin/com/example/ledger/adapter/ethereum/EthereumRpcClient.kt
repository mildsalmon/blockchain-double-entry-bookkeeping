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
import java.nio.charset.StandardCharsets

private const val ERC20_SYMBOL_SELECTOR = "0x95d89b41"
private const val ERC20_DECIMALS_SELECTOR = "0x313ce567"

@Component
class EthereumRpcClient(
    @Value("\${app.ethereum.rpc-url}") rpcUrl: String,
    @Value("\${app.ethereum.max-response-bytes:16777216}") maxResponseBytes: Int,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EthereumRpcClient::class.java)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(rpcUrl.trimEnd('/'))
        .codecs { codecs ->
            codecs.defaultCodecs().maxInMemorySize(maxResponseBytes.coerceAtLeast(262_144))
        }
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

    fun getTokenSymbol(tokenAddress: String, blockNumber: Long? = null): String? {
        val callObject = mapOf(
            "to" to tokenAddress,
            "data" to ERC20_SYMBOL_SELECTOR
        )
        val blockTag = blockNumber?.toHex() ?: "latest"
        val payload = buildRequest("eth_call", listOf(callObject, blockTag))

        val responseBody = post(payload) ?: return null
        val response: RpcResponse<String> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<String>>() {}
        )
        response.error?.let {
            log.warn("eth_call(symbol) RPC error {}: {}", it.code, it.message)
            return null
        }
        return decodeTokenSymbol(response.result)
    }

    fun getTokenDecimals(tokenAddress: String, blockNumber: Long? = null): Int? {
        val callObject = mapOf(
            "to" to tokenAddress,
            "data" to ERC20_DECIMALS_SELECTOR
        )
        val blockTag = blockNumber?.toHex() ?: "latest"
        val payload = buildRequest("eth_call", listOf(callObject, blockTag))

        val responseBody = post(payload) ?: return null
        val response: RpcResponse<String> = objectMapper.readValue(
            responseBody,
            object : TypeReference<RpcResponse<String>>() {}
        )
        response.error?.let {
            log.warn("eth_call(decimals) RPC error {}: {}", it.code, it.message)
            return null
        }
        return decodeTokenDecimals(response.result)
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

private fun decodeTokenDecimals(hexValue: String?): Int? {
    val clean = hexValue?.removePrefix("0x")?.trim().orEmpty()
    if (clean.isEmpty()) return null
    return try {
        BigInteger(clean, 16).toInt()
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

private fun decodeTokenSymbol(hexValue: String?): String? {
    val clean = hexValue?.removePrefix("0x")?.trim().orEmpty()
    if (clean.isEmpty()) return null

    decodeDynamicString(clean)?.let { return sanitizeTokenSymbol(it) }
    decodeFixedBytesString(clean)?.let { return sanitizeTokenSymbol(it) }
    return null
}

private fun decodeDynamicString(cleanHex: String): String? {
    if (cleanHex.length < 128) return null
    val lengthHex = cleanHex.substring(64, 128)
    val byteLength = try {
        BigInteger(lengthHex, 16).toInt()
    } catch (_: NumberFormatException) {
        return null
    } catch (_: ArithmeticException) {
        return null
    }
    if (byteLength <= 0) return ""

    val dataStart = 128
    val dataEnd = dataStart + byteLength * 2
    if (cleanHex.length < dataEnd) return null
    return decodeUtf8(cleanHex.substring(dataStart, dataEnd))
}

private fun decodeFixedBytesString(cleanHex: String): String? {
    if (cleanHex.length < 64) return null
    return decodeUtf8(cleanHex.substring(0, 64))
}

private fun decodeUtf8(hexChunk: String): String? {
    val bytes = hexToByteArray(hexChunk) ?: return null
    val normalized = bytes.takeWhile { byte -> byte.toInt() != 0 }.toByteArray()
    if (normalized.isEmpty()) return null
    return String(normalized, StandardCharsets.UTF_8)
}

private fun hexToByteArray(hex: String): ByteArray? {
    if (hex.length % 2 != 0) return null
    return try {
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    } catch (_: NumberFormatException) {
        null
    }
}

private fun sanitizeTokenSymbol(symbol: String): String? {
    val clean = symbol
        .trim()
        .filterNot { it.isISOControl() }
        .trim()
    return clean.ifBlank { null }
}

class EthereumRpcException(
    val code: Int,
    val rpcMessage: String
) : RuntimeException("Ethereum RPC error(code=$code): $rpcMessage")
