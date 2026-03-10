package com.example.ledger.domain.model

import java.math.BigDecimal
import java.time.Instant

data class CostBasisLot(
    val id: Long? = null,
    val walletAddress: String,
    val tokenSymbol: String,
    val chain: String? = null,
    val tokenAddress: String? = null,
    val acquisitionDate: Instant,
    val quantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val unitCostKrw: BigDecimal,
    val rawTransactionId: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(quantity >= BigDecimal.ZERO) { "quantity must be >= 0" }
        require(remainingQuantity >= BigDecimal.ZERO) { "remainingQuantity must be >= 0" }
        require(remainingQuantity <= quantity) { "remainingQuantity must be <= quantity" }
    }

    fun consume(consumedQty: BigDecimal): CostBasisLot {
        require(consumedQty > BigDecimal.ZERO) { "consumedQty must be > 0" }
        if (consumedQty > remainingQuantity) {
            throw IllegalArgumentException("Not enough remaining quantity in lot")
        }
        return copy(remainingQuantity = remainingQuantity - consumedQty, updatedAt = Instant.now())
    }
}
