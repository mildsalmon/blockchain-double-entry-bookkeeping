package com.example.ledger.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant

data class AccountingEvent(
    val id: Long? = null,
    val rawTransactionId: Long,
    val eventType: EventType,
    val classifierId: String,
    val tokenAddress: String? = null,
    val tokenSymbol: String? = null,
    val amountRaw: BigInteger,
    val amountDecimal: BigDecimal,
    val counterparty: String? = null,
    val priceKrw: BigDecimal? = null,
    val priceSource: PriceSource = PriceSource.UNKNOWN,
    val metadata: Map<String, Any?> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
