package com.example.ledger.domain.port

import com.example.ledger.domain.model.PriceInfo
import java.time.LocalDate

interface PriceCacheRepository {
    fun find(tokenAddress: String?, tokenSymbol: String, date: LocalDate): PriceInfo?
    fun save(info: PriceInfo): PriceInfo
}
