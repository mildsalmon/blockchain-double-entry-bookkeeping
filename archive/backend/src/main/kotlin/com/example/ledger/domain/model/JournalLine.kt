package com.example.ledger.domain.model

import java.math.BigDecimal
import java.time.Instant

data class JournalLine(
    val id: Long? = null,
    val accountCode: String,
    val debitAmount: BigDecimal,
    val creditAmount: BigDecimal,
    val tokenSymbol: String? = null,
    val chain: String? = null,
    val tokenAddress: String? = null,
    val tokenQuantity: BigDecimal? = null,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(debitAmount >= BigDecimal.ZERO) { "debitAmount must be >= 0" }
        require(creditAmount >= BigDecimal.ZERO) { "creditAmount must be >= 0" }
    }
}
