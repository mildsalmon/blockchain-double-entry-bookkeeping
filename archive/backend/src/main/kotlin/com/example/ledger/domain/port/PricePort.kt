package com.example.ledger.domain.port

import com.example.ledger.domain.model.PriceInfo
import java.time.LocalDate

interface PricePort {
    fun getPrice(tokenAddress: String?, tokenSymbol: String, date: LocalDate): PriceInfo

    fun getPricesInRange(
        tokenAddress: String?,
        tokenSymbol: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): Map<LocalDate, PriceInfo>
}
