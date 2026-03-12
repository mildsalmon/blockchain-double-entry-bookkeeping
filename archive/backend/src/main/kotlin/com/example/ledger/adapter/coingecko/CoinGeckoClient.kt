package com.example.ledger.adapter.coingecko

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Component
class CoinGeckoClient(
    @Value("\${app.coingecko.base-url}") private val baseUrl: String,
    @Value("\${app.coingecko.api-key:}") private val apiKey: String,
    private val objectMapper: ObjectMapper
) {
    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders { headers ->
                if (apiKey.isNotBlank()) {
                    headers.add("x-cg-pro-api-key", apiKey)
                }
            }
            .build()
    }

    fun fetchRangePrices(coinId: String, fromDate: LocalDate, toDate: LocalDate): Map<LocalDate, BigDecimal> {
        val fromEpoch = fromDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val toEpoch = toDate.plusDays(1).atStartOfDay().minusSeconds(1).toEpochSecond(ZoneOffset.UTC)

        val response = webClient.get()
            .uri("/coins/{id}/market_chart/range?vs_currency=krw&from={from}&to={to}", coinId, fromEpoch, toEpoch)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: return emptyMap()

        val root = objectMapper.readTree(response)
        val prices = root.path("prices")

        return prices
            .mapNotNull { row ->
                if (!row.isArray || row.size() < 2) return@mapNotNull null
                val timestampMillis = row[0].asLong()
                val date = Instant.ofEpochMilli(timestampMillis).atOffset(ZoneOffset.UTC).toLocalDate()
                val value = row[1].decimalValue()
                date to value
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.last() }
    }
}
