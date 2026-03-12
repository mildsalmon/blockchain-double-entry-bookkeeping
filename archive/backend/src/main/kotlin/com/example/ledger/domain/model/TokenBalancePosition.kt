package com.example.ledger.domain.model

import java.math.BigDecimal
import java.time.Instant

data class TokenBalancePosition(
    val walletAddress: String,
    val accountCode: String,
    val tokenSymbol: String,
    val chain: String? = null,
    val tokenAddress: String? = null,
    val quantity: BigDecimal,
    val lastEntryDate: Instant
)
