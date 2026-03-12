package com.example.ledger.adapter.coingecko

import org.springframework.stereotype.Component

@Component
class TokenIdMapper {
    private val symbolToId = mapOf(
        "ETH" to "ethereum",
        "WETH" to "weth",
        "USDT" to "tether",
        "USDC" to "usd-coin",
        "DAI" to "dai"
    )

    private val addressToId = mapOf(
        "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2" to "weth",
        "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" to "usd-coin",
        "0xdac17f958d2ee523a2206206994597c13d831ec7" to "tether",
        "0x6b175474e89094c44da98b954eedeac495271d0f" to "dai"
    )

    fun resolve(tokenAddress: String?, tokenSymbol: String): String? {
        val byAddress = tokenAddress?.lowercase()?.let { addressToId[it] }
        return byAddress ?: symbolToId[tokenSymbol.uppercase()]
    }
}
