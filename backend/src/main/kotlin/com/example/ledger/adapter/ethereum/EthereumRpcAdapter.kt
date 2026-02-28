package com.example.ledger.adapter.ethereum

import com.example.ledger.adapter.common.RetryExecutor
import com.example.ledger.adapter.ethereum.dto.BlockResponse
import com.example.ledger.adapter.ethereum.dto.LogEntry
import com.example.ledger.adapter.ethereum.dto.TransactionReceiptResponse
import com.example.ledger.adapter.ethereum.dto.TransactionResponse
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.port.BlockchainDataPort
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigInteger
import java.time.Instant

private const val BLOCK_CHUNK_SIZE = 10_000L

private const val TOPIC_ERC20_TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
private const val TOPIC_ERC1155_TRANSFER_SINGLE = "0xc3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f62"
private const val TOPIC_ERC1155_TRANSFER_BATCH = "0x4a39dc06d4c0dbc64b70af90fd698a233a518aa5d07e595d983b8c0526c8f7fb"

@Component
class EthereumRpcAdapter(
    private val rpcClient: EthereumRpcClient,
    private val objectMapper: ObjectMapper,
    private val retryExecutor: RetryExecutor
) : BlockchainDataPort {

    private val log = LoggerFactory.getLogger(EthereumRpcAdapter::class.java)

    override fun fetchTransactions(walletAddress: String, fromBlock: Long?): List<RawTransaction> {
        val latestBlock = retryExecutor.execute { rpcClient.getBlockNumber() }
        val startBlock = fromBlock ?: 0L

        log.info("Fetching transactions for {} from block {} to {}", walletAddress, startBlock, latestBlock)

        val chunks = blockChunks(startBlock, latestBlock)
        val collectedTxHashes = mutableSetOf<String>()
        val blockTimestampCache = mutableMapOf<Long, Long>()
        val txCache = mutableMapOf<String, TransactionResponse>()

        val logEntries = collectEventLogs(chunks, walletAddress)
        logEntries.forEach { entry ->
            entry.transactionHash?.let { collectedTxHashes.add(it.lowercase()) }
        }

        collectNativeTransfers(chunks, walletAddress, collectedTxHashes, blockTimestampCache, txCache)

        log.info("Collected {} unique tx hashes for {}", collectedTxHashes.size, walletAddress)

        return buildRawTransactions(walletAddress, collectedTxHashes, blockTimestampCache, txCache)
    }

    override fun getNativeBalanceAtBlock(walletAddress: String, blockNumber: Long): BigInteger {
        return retryExecutor.execute { rpcClient.getNativeBalanceAtBlock(walletAddress, blockNumber) }
    }

    override fun getTokenBalanceAtBlock(walletAddress: String, tokenAddress: String, blockNumber: Long): BigInteger {
        return retryExecutor.execute { rpcClient.getTokenBalanceAtBlock(walletAddress, tokenAddress, blockNumber) }
    }

    private fun blockChunks(fromBlock: Long, toBlock: Long): List<Pair<Long, Long>> {
        val chunks = mutableListOf<Pair<Long, Long>>()
        var start = fromBlock
        while (start <= toBlock) {
            val end = minOf(start + BLOCK_CHUNK_SIZE - 1, toBlock)
            chunks.add(start to end)
            start = end + 1
        }
        return chunks
    }

    private fun collectEventLogs(
        chunks: List<Pair<Long, Long>>,
        walletAddress: String
    ): List<LogEntry> {
        val walletLower = walletAddress.lowercase()
        val paddedWallet = "0x000000000000000000000000${walletLower.removePrefix("0x")}"
        val eventTopics = listOf(
            TOPIC_ERC20_TRANSFER,
            TOPIC_ERC1155_TRANSFER_SINGLE,
            TOPIC_ERC1155_TRANSFER_BATCH
        )

        val result = mutableListOf<LogEntry>()
        for ((from, to) in chunks) {
            for (topic0 in eventTopics) {
                val logs = fetchWithAdaptiveRange(
                    fromBlock = from,
                    toBlock = to,
                    fetch = { start, end ->
                        retryExecutor.execute {
                            rpcClient.getLogs(start, end, listOf(topic0))
                        }
                    },
                    shouldSplit = { error -> shouldSplitLogRangeError(error) }
                )
                val relevant = logs.filter { log -> isWalletRelatedLog(log, paddedWallet) }
                result.addAll(relevant)
            }
        }
        return result
    }

    private fun collectNativeTransfers(
        chunks: List<Pair<Long, Long>>,
        walletAddress: String,
        collectedTxHashes: MutableSet<String>,
        blockTimestampCache: MutableMap<Long, Long>,
        txCache: MutableMap<String, TransactionResponse>
    ) {
        val walletLower = walletAddress.lowercase()
        for ((chunkFrom, chunkTo) in chunks) {
            for (blockNumber in chunkFrom..chunkTo) {
                val block = retryExecutor.execute {
                    rpcClient.getBlockByNumber(blockNumber, fullTransactions = true)
                } ?: continue

                val timestamp = block.timestamp?.hexToLong() ?: continue
                blockTimestampCache[blockNumber] = timestamp

                block.transactions.orEmpty().forEach { tx ->
                    val txFrom = tx.from?.lowercase()
                    val txTo = tx.to?.lowercase()
                    if (txFrom == walletLower || txTo == walletLower) {
                        val hash = tx.hash?.lowercase() ?: return@forEach
                        collectedTxHashes.add(hash)
                        txCache[hash] = tx
                    }
                }
            }
        }
    }

    private fun buildRawTransactions(
        walletAddress: String,
        txHashes: Set<String>,
        blockTimestampCache: MutableMap<Long, Long>,
        txCache: MutableMap<String, TransactionResponse>
    ): List<RawTransaction> {
        val results = mutableListOf<RawTransaction>()

        for (txHash in txHashes) {
            val receipt = retryExecutor.execute { rpcClient.getTransactionReceipt(txHash) }
            if (receipt == null) {
                log.warn("No receipt found for txHash {}", txHash)
                continue
            }

            val blockNumber = receipt.blockNumber?.hexToLong() ?: continue

            val timestamp = blockTimestampCache.getOrPut(blockNumber) {
                retryExecutor.execute {
                    rpcClient.getBlockByNumber(blockNumber, fullTransactions = false)
                        ?.timestamp
                        ?.hexToLong()
                } ?: return@getOrPut 0L
            }

            val tx = txCache[txHash] ?: retryExecutor.execute {
                rpcClient.getTransactionByHash(txHash)
            }

            val blockTimestamp = Instant.ofEpochSecond(timestamp)
            val txStatus = if (receipt.status == "0x1") 1 else 0
            val txIndex = receipt.transactionIndex?.hexToInt()

            val rawData = objectMapper.createObjectNode().apply {
                set<com.fasterxml.jackson.databind.node.ObjectNode>("transaction", buildTransactionNode(tx, receipt))
                set<com.fasterxml.jackson.databind.node.ObjectNode>("receipt", buildReceiptNode(receipt))
            }

            results.add(
                RawTransaction(
                    walletAddress = walletAddress,
                    txHash = txHash,
                    blockNumber = blockNumber,
                    txIndex = txIndex,
                    blockTimestamp = blockTimestamp,
                    rawData = rawData,
                    txStatus = txStatus
                )
            )
        }

        return results
    }

    private fun buildTransactionNode(
        tx: TransactionResponse?,
        receipt: TransactionReceiptResponse
    ): com.fasterxml.jackson.databind.node.ObjectNode {
        return objectMapper.createObjectNode().apply {
            put("hash", receipt.transactionHash)
            put("blockNumber", receipt.blockNumber)
            put("blockHash", receipt.blockHash)
            put("transactionIndex", receipt.transactionIndex)
            put("from", tx?.from ?: receipt.from)
            put("to", tx?.to ?: receipt.to)
            put("value", tx?.value ?: "0x0")
            put("gas", tx?.gas)
            put("gasPrice", tx?.gasPrice)
            put("input", tx?.input)
            put("nonce", tx?.nonce)
        }
    }

    private fun buildReceiptNode(receipt: TransactionReceiptResponse): com.fasterxml.jackson.databind.node.ObjectNode {
        return objectMapper.createObjectNode().apply {
            put("transactionHash", receipt.transactionHash)
            put("blockNumber", receipt.blockNumber)
            put("blockHash", receipt.blockHash)
            put("transactionIndex", receipt.transactionIndex)
            put("from", receipt.from)
            put("to", receipt.to)
            put("status", receipt.status)
            put("gasUsed", receipt.gasUsed)
            put("effectiveGasPrice", receipt.effectiveGasPrice)
            put("contractAddress", receipt.contractAddress)
            set<com.fasterxml.jackson.databind.node.ArrayNode>(
                "logs",
                objectMapper.valueToTree(receipt.logs ?: emptyList<LogEntry>())
            )
        }
    }
}

private fun String.hexToLong(): Long = removePrefix("0x").ifEmpty { "0" }.toLong(16)
private fun String.hexToInt(): Int = removePrefix("0x").ifEmpty { "0" }.toInt(16)

internal fun isWalletRelatedLog(log: LogEntry, paddedWalletTopic: String): Boolean {
    return log.topics.any { it.equals(paddedWalletTopic, ignoreCase = true) }
}

internal fun isTooManyResultsError(error: EthereumRpcException): Boolean {
    val message = error.rpcMessage.lowercase()
    return error.code == -32005
        || error.code == -32016
        || error.code == -32602
        || message.contains("too many results")
        || message.contains("result set too large")
        || message.contains("query returned more than")
        || message.contains("query exceeds max results")
        || message.contains("retry with the range")
}

internal fun shouldSplitLogRangeError(error: Throwable): Boolean {
    if (error is JsonParseException || hasCause<JsonParseException>(error)) {
        return true
    }
    if (error is JsonMappingException || hasCause<JsonMappingException>(error)) {
        return true
    }
    if (hasMessageInCause(error, "unexpected end-of-input") || hasMessageInCause(error, "unexpected close marker")) {
        return true
    }
    if (error is DataBufferLimitException || hasCause<DataBufferLimitException>(error)) {
        return true
    }
    if (error is EthereumRpcException && isTooManyResultsError(error)) {
        return true
    }
    if (error is WebClientResponseException) {
        val statusCode = error.statusCode.value()
        return statusCode == 408 || statusCode == 429 || statusCode >= 500
    }
    return false
}

private inline fun <reified T : Throwable> hasCause(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is T) {
            return true
        }
        current = current.cause
    }
    return false
}

private fun hasMessageInCause(error: Throwable, messageFragment: String): Boolean {
    var current: Throwable? = error
    while (current != null) {
        val message = current.message
        if (message != null && message.contains(messageFragment, ignoreCase = true)) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun <T> fetchWithAdaptiveRange(
    fromBlock: Long,
    toBlock: Long,
    fetch: (Long, Long) -> List<T>,
    shouldSplit: (Throwable) -> Boolean
): List<T> {
    return try {
        fetch(fromBlock, toBlock)
    } catch (error: Throwable) {
        if (!shouldSplit(error) || fromBlock >= toBlock) {
            throw error
        }

        val mid = fromBlock + ((toBlock - fromBlock) / 2)
        val left = fetchWithAdaptiveRange(fromBlock, mid, fetch, shouldSplit)
        val right = fetchWithAdaptiveRange(mid + 1, toBlock, fetch, shouldSplit)
        left + right
    }
}
