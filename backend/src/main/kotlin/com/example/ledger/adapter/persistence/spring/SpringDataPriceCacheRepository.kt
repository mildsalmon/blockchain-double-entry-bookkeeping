package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.PriceCacheEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SpringDataPriceCacheRepository : JpaRepository<PriceCacheEntity, Long> {
    fun findByTokenAddressAndTokenSymbolAndPriceDate(tokenAddress: String?, tokenSymbol: String, priceDate: LocalDate): PriceCacheEntity?
}
