package com.example.ledger.application.dto

import java.time.Instant

data class BalancePositionResponse(
    val walletAddress: String,
    val accountCode: String,
    val tokenSymbol: String,
    val chain: String?,
    val chainLabel: String,
    val tokenAddress: String?,
    val displayLabel: String,
    val quantity: String,
    val lastEntryDate: Instant
)

data class BalanceSummaryResponse(
    val walletCount: Int,
    val tokenCount: Int,
    val positionCount: Int
)

data class BalanceDashboardResponse(
    val generatedAt: Instant,
    val summary: BalanceSummaryResponse,
    val positions: List<BalancePositionResponse>
)
