package com.example.ledger.adapter.web3

import com.example.ledger.adapter.web3.abi.ERC20ABI
import com.example.ledger.adapter.web3.abi.UniswapV2ABI
import com.example.ledger.adapter.web3.abi.UniswapV3ABI
import com.example.ledger.domain.model.DecodedEvent
import com.example.ledger.domain.model.DecodedTransaction
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.SwapEvent
import com.example.ledger.domain.model.TransferEvent
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.BigInteger

@Component
class EventDecoder {
    companion object {
        private val TWO_POW_256: BigInteger = BigInteger.ONE.shiftLeft(256)
        private val WEI_PER_ETH: BigDecimal = BigDecimal.TEN.pow(18)
    }

    fun decode(rawTransaction: RawTransaction): DecodedTransaction {
        val txNode = rawTransaction.rawData.path("transaction")
        val receiptNode = rawTransaction.rawData.path("receipt")

        val from = txNode.path("from").asText(null)
        val to = txNode.path("to").asText(null)

        val valueHex = txNode.path("value").asText("0x0")
        val nativeValue = hexWeiToEth(valueHex)

        val gasUsedHex = receiptNode.path("gasUsed").asText("0x0")
        val effectiveGasPriceHex = receiptNode.path("effectiveGasPrice").asText("0x0")
        val gasFee = calculateGasFeeEth(gasUsedHex, effectiveGasPriceHex)

        val events = mutableListOf<DecodedEvent>()
        events += decodeReceiptLogs(receiptNode.path("logs"))

        return DecodedTransaction(
            rawTransaction = rawTransaction,
            from = from,
            to = to,
            nativeValue = nativeValue,
            gasUsedEth = gasFee,
            events = events
        )
    }

    private fun hexWeiToEth(hexValue: String): BigDecimal {
        val clean = hexValue.removePrefix("0x").ifEmpty { "0" }
        val wei = BigInteger(clean, 16)
        return wei.toBigDecimal().divide(WEI_PER_ETH)
    }

    private fun calculateGasFeeEth(gasUsedHex: String, effectiveGasPriceHex: String): BigDecimal {
        val gasUsed = BigInteger(gasUsedHex.removePrefix("0x").ifEmpty { "0" }, 16)
        val effectiveGasPrice = BigInteger(effectiveGasPriceHex.removePrefix("0x").ifEmpty { "0" }, 16)
        val gasFeeWei = gasUsed.multiply(effectiveGasPrice)
        return gasFeeWei.toBigDecimal().divide(WEI_PER_ETH)
    }

    private fun decodeReceiptLogs(logsNode: JsonNode): List<DecodedEvent> {
        if (!logsNode.isArray) return emptyList()
        return logsNode.mapNotNull { decodeLog(it) }
    }

    private fun decodeLog(log: JsonNode): DecodedEvent? {
        val topicsNode = log.path("topics")
        if (!topicsNode.isArray || topicsNode.size() == 0) return null

        val topic0 = normalizeHex(topicsNode[0].asText(null))
        return when (topic0) {
            normalizeHex(ERC20ABI.TRANSFER_TOPIC) -> decodeErc20TransferLog(log, topicsNode)
            normalizeHex(UniswapV2ABI.SWAP_TOPIC) -> decodeUniswapV2SwapLog(log)
            normalizeHex(UniswapV3ABI.SWAP_TOPIC) -> decodeUniswapV3SwapLog(log)
            else -> null
        }
    }

    private fun decodeErc20TransferLog(log: JsonNode, topicsNode: JsonNode): TransferEvent? {
        val from = decodeAddressFromTopic(topicsNode.path(1).asText(null)) ?: return null
        val to = decodeAddressFromTopic(topicsNode.path(2).asText(null)) ?: return null
        val amount = decodeUnsigned256(log.path("data").asText(null), 0)?.toBigDecimal() ?: return null

        return TransferEvent(
            tokenAddress = normalizeAddress(log.path("address").asText(null)),
            tokenSymbol = log.path("tokenSymbol").asText(log.path("symbol").asText("UNKNOWN")),
            from = from,
            to = to,
            amount = amount
        )
    }

    private fun decodeUniswapV2SwapLog(log: JsonNode): SwapEvent? {
        val amount0In = decodeUnsigned256(log.path("data").asText(null), 0)?.toBigDecimal() ?: return null
        val amount1In = decodeUnsigned256(log.path("data").asText(null), 1)?.toBigDecimal() ?: return null
        val amount0Out = decodeUnsigned256(log.path("data").asText(null), 2)?.toBigDecimal() ?: return null
        val amount1Out = decodeUnsigned256(log.path("data").asText(null), 3)?.toBigDecimal() ?: return null

        val token0Address = normalizeAddress(log.path("token0").asText(null))
        val token1Address = normalizeAddress(log.path("token1").asText(null))
        val token0Symbol = log.path("token0Symbol").asText("TOKEN0")
        val token1Symbol = log.path("token1Symbol").asText("TOKEN1")

        val amountIn: BigDecimal
        val tokenInAddress: String?
        val tokenInSymbol: String
        if (amount0In > BigDecimal.ZERO) {
            amountIn = amount0In
            tokenInAddress = token0Address
            tokenInSymbol = token0Symbol
        } else {
            amountIn = amount1In
            tokenInAddress = token1Address
            tokenInSymbol = token1Symbol
        }

        val amountOut: BigDecimal
        val tokenOutAddress: String?
        val tokenOutSymbol: String
        if (amount0Out > BigDecimal.ZERO) {
            amountOut = amount0Out
            tokenOutAddress = token0Address
            tokenOutSymbol = token0Symbol
        } else {
            amountOut = amount1Out
            tokenOutAddress = token1Address
            tokenOutSymbol = token1Symbol
        }

        if (amountIn <= BigDecimal.ZERO && amountOut <= BigDecimal.ZERO) return null

        return SwapEvent(
            protocol = "uniswap_v2",
            tokenInAddress = tokenInAddress,
            tokenInSymbol = tokenInSymbol,
            amountIn = amountIn,
            tokenOutAddress = tokenOutAddress,
            tokenOutSymbol = tokenOutSymbol,
            amountOut = amountOut
        )
    }

    private fun decodeUniswapV3SwapLog(log: JsonNode): SwapEvent? {
        val amount0 = decodeSigned256(log.path("data").asText(null), 0)?.toBigDecimal() ?: return null
        val amount1 = decodeSigned256(log.path("data").asText(null), 1)?.toBigDecimal() ?: return null

        val token0Address = normalizeAddress(log.path("token0").asText(null))
        val token1Address = normalizeAddress(log.path("token1").asText(null))
        val token0Symbol = log.path("token0Symbol").asText("TOKEN0")
        val token1Symbol = log.path("token1Symbol").asText("TOKEN1")

        if (amount0 == BigDecimal.ZERO && amount1 == BigDecimal.ZERO) return null

        val (tokenInAddress, tokenInSymbol, amountIn) = when {
            amount0 > BigDecimal.ZERO -> Triple(token0Address, token0Symbol, amount0)
            amount1 > BigDecimal.ZERO -> Triple(token1Address, token1Symbol, amount1)
            else -> Triple(token0Address, token0Symbol, amount0.abs())
        }

        val (tokenOutAddress, tokenOutSymbol, amountOut) = when {
            amount0 < BigDecimal.ZERO -> Triple(token0Address, token0Symbol, amount0.abs())
            amount1 < BigDecimal.ZERO -> Triple(token1Address, token1Symbol, amount1.abs())
            else -> Triple(token1Address, token1Symbol, amount1.abs())
        }

        return SwapEvent(
            protocol = "uniswap_v3",
            tokenInAddress = tokenInAddress,
            tokenInSymbol = tokenInSymbol,
            amountIn = amountIn,
            tokenOutAddress = tokenOutAddress,
            tokenOutSymbol = tokenOutSymbol,
            amountOut = amountOut
        )
    }

    private fun decodeUnsigned256(hexData: String?, chunkIndex: Int): BigInteger? {
        val chunk = chunk(hexData, chunkIndex) ?: return null
        return BigInteger(chunk, 16)
    }

    private fun decodeSigned256(hexData: String?, chunkIndex: Int): BigInteger? {
        val chunk = chunk(hexData, chunkIndex) ?: return null
        val unsigned = BigInteger(chunk, 16)
        val isNegative = chunk.first().digitToInt(16) >= 8
        return if (isNegative) unsigned.subtract(TWO_POW_256) else unsigned
    }

    private fun chunk(hexData: String?, chunkIndex: Int): String? {
        val clean = normalizeHex(hexData).removePrefix("0x")
        val start = chunkIndex * 64
        val end = start + 64
        if (clean.length < end) return null
        return clean.substring(start, end)
    }

    private fun decodeAddressFromTopic(topic: String?): String? {
        val clean = normalizeHex(topic).removePrefix("0x")
        if (clean.length < 40) return null
        return "0x${clean.takeLast(40)}"
    }

    private fun normalizeAddress(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val normalized = normalizeHex(address)
        if (normalized.length < 42) return normalized
        return "0x${normalized.removePrefix("0x").takeLast(40)}"
    }

    private fun normalizeHex(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val clean = value.trim().lowercase()
        return if (clean.startsWith("0x")) clean else "0x$clean"
    }
}
