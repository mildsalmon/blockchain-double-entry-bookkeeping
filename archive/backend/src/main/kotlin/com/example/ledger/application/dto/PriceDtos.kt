package com.example.ledger.application.dto

import com.example.ledger.domain.model.PriceSource
import java.math.BigDecimal
import java.time.LocalDate

data class PriceResponse(
    val token: String,
    val date: LocalDate,
    val priceKrw: BigDecimal,
    val source: PriceSource
)
