package com.example.ledger.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class PriceInfo(
    val tokenAddress: String? = null,
    val tokenSymbol: String,
    val date: LocalDate,
    val priceKrw: BigDecimal,
    val source: PriceSource
)
