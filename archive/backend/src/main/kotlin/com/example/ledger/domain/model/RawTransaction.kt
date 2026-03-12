package com.example.ledger.domain.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

data class RawTransaction(
    val id: Long? = null,
    val walletAddress: String,
    val txHash: String,
    val blockNumber: Long,
    val txIndex: Int? = null,
    val blockTimestamp: Instant,
    val rawData: JsonNode,
    val txStatus: Int,
    val createdAt: Instant = Instant.now()
)
