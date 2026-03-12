package com.example.ledger.adapter.coingecko

import com.example.ledger.adapter.common.RateLimiter
import com.example.ledger.adapter.common.RetryExecutor
import com.example.ledger.domain.model.PriceInfo
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.port.PriceCacheRepository
import com.example.ledger.domain.port.PricePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal
import java.time.LocalDate

@Component
class CoinGeckoAdapter(
    private val coinGeckoClient: CoinGeckoClient,
    private val tokenIdMapper: TokenIdMapper,
    private val priceCacheRepository: PriceCacheRepository,
    private val coinGeckoRateLimiter: RateLimiter,
    private val retryExecutor: RetryExecutor
) : PricePort {

    private val logger = LoggerFactory.getLogger(CoinGeckoAdapter::class.java)

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

        val prices = try {
            retryExecutor.execute {
                coinGeckoClient.fetchRangePrices(tokenId, fromDate, toDate)
            }
        } catch (ex: WebClientResponseException) {
            val status = ex.statusCode.value()
            if (status in 400..499) {
                logger.warn(
                    "CoinGecko returned {}for tokenId={}, fromDate={}, toDate={}: returning empty prices",
                    "$status ", tokenId, fromDate, toDate
                )
            } else {
                logger.error(
                    "CoinGecko returned {} for tokenId={}, fromDate={}, toDate={} after retries exhausted: degraded mode",
                    status, tokenId, fromDate, toDate
                )
            }
            return emptyMap()
        }

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
