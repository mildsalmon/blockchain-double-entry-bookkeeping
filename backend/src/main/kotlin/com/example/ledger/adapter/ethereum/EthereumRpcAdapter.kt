package com.example.ledger.adapter.ethereum

import com.example.ledger.adapter.common.RetryExecutor
import com.example.ledger.adapter.ethereum.dto.BlockResponse
import com.example.ledger.adapter.ethereum.dto.LogEntry
import com.example.ledger.adapter.ethereum.dto.TransactionReceiptResponse
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.port.BlockchainDataPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

private const val BLOCK_CHUNK_SIZE = 10_000L

private const val TOPIC_ERC20_TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
private const val TOPIC_ERC1155_TRANSFER_SINGLE = "0xc3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f62"
private const val TOPIC_ERC1155_TRANSFER_BATCH = "0x4a39dc06d4c0dbc64b70af90fd698a233a518aa5d07e595d983b8c0526c8f7fb"
private const val TOPIC_UNISWAP_V2_SWAP = "0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822"
private const val TOPIC_UNISWAP_V3_SWAP = "0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67"

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

        val logEntries = collectEventLogs(chunks, walletAddress)
        logEntries.forEach { entry ->
            entry.transactionHash?.let { collectedTxHashes.add(it.lowercase()) }
        }

        collectNativeTransfers(chunks, walletAddress, collectedTxHashes, blockTimestampCache)

        log.info("Collected {} unique tx hashes for {}", collectedTxHashes.size, walletAddress)

        return buildRawTransactions(walletAddress, collectedTxHashes, blockTimestampCache)
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
            TOPIC_ERC1155_TRANSFER_BATCH,
            TOPIC_UNISWAP_V2_SWAP,
            TOPIC_UNISWAP_V3_SWAP
        )

        val result = mutableListOf<LogEntry>()
        for ((from, to) in chunks) {
            for (topic0 in eventTopics) {
                val logs = retryExecutor.execute {
                    rpcClient.getLogs(from, to, listOf(topic0))
                }
                val relevant = logs.filter { log ->
                    log.topics.any { it.lowercase() == paddedWallet }
                        || topic0 == TOPIC_UNISWAP_V2_SWAP
                        || topic0 == TOPIC_UNISWAP_V3_SWAP
                }
                result.addAll(relevant)
            }
        }
        return result
    }

    private fun collectNativeTransfers(
        chunks: List<Pair<Long, Long>>,
        walletAddress: String,
        collectedTxHashes: MutableSet<String>,
        blockTimestampCache: MutableMap<Long, Long>
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
                        tx.hash?.lowercase()?.let { collectedTxHashes.add(it) }
                    }
                }
            }
        }
    }

    private fun buildRawTransactions(
        walletAddress: String,
        txHashes: Set<String>,
        blockTimestampCache: MutableMap<Long, Long>
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

            val blockTimestamp = Instant.ofEpochSecond(timestamp)
            val txStatus = if (receipt.status == "0x1") 1 else 0
            val txIndex = receipt.transactionIndex?.hexToInt()

            val transactionNode = buildTransactionNode(receipt)
            val rawData = objectMapper.createObjectNode().apply {
                set<com.fasterxml.jackson.databind.node.ObjectNode>("transaction", transactionNode)
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

    private fun buildTransactionNode(receipt: TransactionReceiptResponse): com.fasterxml.jackson.databind.node.ObjectNode {
        return objectMapper.createObjectNode().apply {
            put("hash", receipt.transactionHash)
            put("blockNumber", receipt.blockNumber)
            put("blockHash", receipt.blockHash)
            put("transactionIndex", receipt.transactionIndex)
            put("from", receipt.from)
            put("to", receipt.to)
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
