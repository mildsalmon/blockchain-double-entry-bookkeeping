package com.example.ledger.adapter.ethereum.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class RpcResponse<T>(
    val id: Int?,
    val jsonrpc: String?,
    val result: T?,
    val error: RpcError?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RpcError(
    val code: Int,
    val message: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LogEntry(
    val address: String?,
    val topics: List<String>,
    val data: String?,
    val blockNumber: String?,
    val transactionHash: String?,
    val transactionIndex: String?,
    val blockHash: String?,
    val logIndex: String?,
    val removed: Boolean?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BlockResponse(
    val number: String?,
    val hash: String?,
    val timestamp: String?,
    val transactions: List<TransactionResponse>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionResponse(
    val hash: String?,
    val blockNumber: String?,
    val blockHash: String?,
    val transactionIndex: String?,
    val from: String?,
    val to: String?,
    val value: String?,
    val gas: String?,
    val gasPrice: String?,
    val nonce: String?,
    val input: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionReceiptResponse(
    val transactionHash: String?,
    val blockNumber: String?,
    val blockHash: String?,
    val transactionIndex: String?,
    val from: String?,
    val to: String?,
    val status: String?,
    val gasUsed: String?,
    val effectiveGasPrice: String?,
    val logs: List<LogEntry>?,
    val contractAddress: String?
)
