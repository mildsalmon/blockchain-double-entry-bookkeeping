package com.example.ledger.domain.model

import java.math.BigInteger
import java.time.Instant

data class WalletBalanceSnapshot(
    val id: Long? = null,
    val walletId: Long,
    val tokenAddress: String,
    val tokenSymbol: String,
    val balanceRaw: BigInteger,
    val cutoffBlock: Long,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
