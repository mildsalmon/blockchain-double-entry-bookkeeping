package com.example.ledger.adapter.coingecko

import com.example.ledger.adapter.common.RateLimiter
import com.example.ledger.domain.model.PriceInfo
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.port.PriceCacheRepository
import com.example.ledger.domain.port.PricePort
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class CoinGeckoAdapter(
    private val coinGeckoClient: CoinGeckoClient,
    private val tokenIdMapper: TokenIdMapper,
    private val priceCacheRepository: PriceCacheRepository,
    private val coinGeckoRateLimiter: RateLimiter
) : PricePort {

    override fun getPrice(tokenAddress: String?, tokenSymbol: String, date: LocalDate): PriceInfo {
        val cached = priceCacheRepository.find(tokenAddress, tokenSymbol, date)
        if (cached != null) return cached

        val prices = getPricesInRange(tokenAddress, tokenSymbol, date, date)
        return prices[date] ?: PriceInfo(
            tokenAddress = tokenAddress,
            tokenSymbol = tokenSymbol,
            date = date,
            priceKrw = BigDecimal.ZERO,
            source = PriceSource.UNKNOWN
        ).also { priceCacheRepository.save(it) }
    }

    override fun getPricesInRange(
        tokenAddress: String?,
        tokenSymbol: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): Map<LocalDate, PriceInfo> {
        val tokenId = tokenIdMapper.resolve(tokenAddress, tokenSymbol)
            ?: return dates(fromDate, toDate).associateWith {
                PriceInfo(
                    tokenAddress = tokenAddress,
                    tokenSymbol = tokenSymbol,
                    date = it,
                    priceKrw = BigDecimal.ZERO,
                    source = PriceSource.UNKNOWN
                )
            }

        coinGeckoRateLimiter.acquire()

        val prices = coinGeckoClient.fetchRangePrices(tokenId, fromDate, toDate)
        if (prices.isEmpty()) {
            return dates(fromDate, toDate).associateWith {
                PriceInfo(
                    tokenAddress = tokenAddress,
                    tokenSymbol = tokenSymbol,
                    date = it,
                    priceKrw = BigDecimal.ZERO,
                    source = PriceSource.UNKNOWN
                )
            }
        }

        return prices.mapValues { (date, krw) ->
            val info = PriceInfo(
                tokenAddress = tokenAddress,
                tokenSymbol = tokenSymbol,
                date = date,
                priceKrw = krw,
                source = PriceSource.COINGECKO
            )
            priceCacheRepository.save(info)
        }
    }

    private fun dates(fromDate: LocalDate, toDate: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = fromDate
        while (current <= toDate) {
            dates += current
            current = current.plusDays(1)
        }
        return dates
    }
}
