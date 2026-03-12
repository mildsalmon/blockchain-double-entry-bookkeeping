package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.spring.SpringDataPriceCacheRepository
import com.example.ledger.domain.model.PriceInfo
import com.example.ledger.domain.port.PriceCacheRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class PriceCacheJpaRepository(
    private val springDataPriceCacheRepository: SpringDataPriceCacheRepository
) : PriceCacheRepository {
    override fun find(tokenAddress: String?, tokenSymbol: String, date: LocalDate): PriceInfo? {
        return springDataPriceCacheRepository
            .findByTokenAddressAndTokenSymbolAndPriceDate(tokenAddress, tokenSymbol, date)
            ?.toDomain()
    }

    override fun save(info: PriceInfo): PriceInfo {
        return springDataPriceCacheRepository.save(info.toEntity()).toDomain()
    }
}
