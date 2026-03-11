package com.example.ledger.domain.service

import com.example.ledger.domain.model.TokenMetadata
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.TokenMetadataRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

private const val TOKEN_SYMBOL_MAX_LENGTH = 20
private const val NATIVE_ETH_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000"
private val TOKEN_METADATA_STALE_AFTER: Duration = Duration.ofHours(24)

data class TokenDisplayInfo(
    val tokenSymbol: String,
    val chain: String,
    val chainLabel: String,
    val tokenAddress: String?,
    val displayLabel: String
)

@Service
class TokenMetadataService(
    private val tokenMetadataRepository: TokenMetadataRepository,
    private val blockchainDataPort: BlockchainDataPort
) {
    private val logger = LoggerFactory.getLogger(TokenMetadataService::class.java)

    companion object {
        const val ETHEREUM_CHAIN = "ETHEREUM"
        const val ETHEREUM_LABEL = "Ethereum"
        const val ERR_SYMBOL = "ERR"
    }

    fun resolveForWrite(tokenAddress: String?, fallbackSymbol: String?, blockNumber: Long? = null): TokenDisplayInfo {
        val normalizedAddress = normalizeContractAddress(tokenAddress)
        if (normalizedAddress == null) {
            return displayInfo(normalizeSymbol(fallbackSymbol) ?: "ETH", null)
        }
        return resolveDisplay(
            tokenAddress = normalizedAddress,
            fallbackSymbol = fallbackSymbol,
            blockNumber = blockNumber,
            chain = ETHEREUM_CHAIN
        )
    }

    fun resolveForRead(tokenAddress: String?, fallbackSymbol: String?, chain: String?): TokenDisplayInfo {
        val normalizedAddress = normalizeContractAddress(tokenAddress)
        val resolvedChain = chain?.takeIf { it.isNotBlank() } ?: ETHEREUM_CHAIN
        if (normalizedAddress == null) {
            return displayInfo(normalizeSymbol(fallbackSymbol) ?: ERR_SYMBOL, null, resolvedChain)
        }
        return resolveDisplay(
            tokenAddress = normalizedAddress,
            fallbackSymbol = fallbackSymbol,
            blockNumber = null,
            chain = resolvedChain
        )
    }

    fun resolveForPreview(tokenAddress: String?, fallbackSymbol: String?, blockNumber: Long? = null): TokenDisplayInfo {
        val normalizedAddress = normalizeContractAddress(tokenAddress)
        if (normalizedAddress == null) {
            return displayInfo(normalizeSymbol(fallbackSymbol) ?: "ETH", null)
        }

        val onChainSymbol = fetchOnChainSymbol(normalizedAddress, blockNumber)
        if (onChainSymbol != null) {
            return displayInfo(onChainSymbol, normalizedAddress, ETHEREUM_CHAIN)
        }

        val cached = tokenMetadataRepository.findByChainAndTokenAddress(ETHEREUM_CHAIN, normalizedAddress)
        if (cached != null) {
            return displayInfo(cached.symbol, normalizedAddress, ETHEREUM_CHAIN)
        }

        return displayInfo(normalizeSymbol(fallbackSymbol) ?: ERR_SYMBOL, normalizedAddress, ETHEREUM_CHAIN)
    }

    fun resolveCachedForRead(tokenAddress: String?, fallbackSymbol: String?, chain: String?): TokenDisplayInfo {
        val normalizedAddress = normalizeContractAddress(tokenAddress)
        val resolvedChain = chain?.takeIf { it.isNotBlank() } ?: ETHEREUM_CHAIN
        if (normalizedAddress == null) {
            return displayInfo(normalizeSymbol(fallbackSymbol) ?: ERR_SYMBOL, null, resolvedChain)
        }

        val cached = tokenMetadataRepository.findByChainAndTokenAddress(resolvedChain, normalizedAddress)
        if (cached != null) {
            return displayInfo(cached.symbol, normalizedAddress, resolvedChain)
        }

        return displayInfo(normalizeSymbol(fallbackSymbol) ?: ERR_SYMBOL, normalizedAddress, resolvedChain)
    }

    fun normalizeContractAddress(tokenAddress: String?): String? {
        if (tokenAddress.isNullOrBlank()) return null
        val clean = tokenAddress.removePrefix("0x").lowercase()
        if (clean.length != 40) return null
        if (clean == NATIVE_ETH_TOKEN_ADDRESS.removePrefix("0x")) return null
        return "0x$clean"
    }

    fun normalizeSymbol(symbol: String?): String? {
        return symbol
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(TOKEN_SYMBOL_MAX_LENGTH)
            ?.uppercase()
            ?.takeIf { it != "UNKNOWN" && it != "ERC20" }
    }

    private fun resolveDisplay(
        tokenAddress: String,
        fallbackSymbol: String?,
        blockNumber: Long?,
        chain: String
    ): TokenDisplayInfo {
        val cached = tokenMetadataRepository.findByChainAndTokenAddress(chain, tokenAddress)
        if (cached != null && !isStale(cached.lastVerifiedAt)) {
            return displayInfo(cached.symbol, tokenAddress, chain)
        }

        val onChainSymbol = fetchOnChainSymbol(tokenAddress, blockNumber)
        if (onChainSymbol != null) {
            val metadata = persistMetadata(chain, tokenAddress, onChainSymbol, cached)
            return displayInfo(metadata.symbol, tokenAddress, chain)
        }

        if (cached != null) {
            return displayInfo(cached.symbol, tokenAddress, chain)
        }

        val fallbackResolved = normalizeSymbol(fallbackSymbol)
        if (fallbackResolved != null) {
            return displayInfo(fallbackResolved, tokenAddress, chain)
        }

        return displayInfo(ERR_SYMBOL, tokenAddress, chain)
    }

    private fun fetchOnChainSymbol(tokenAddress: String, blockNumber: Long?): String? {
        return try {
            normalizeSymbol(blockchainDataPort.getTokenSymbol(tokenAddress, blockNumber))
        } catch (ex: Exception) {
            logger.warn("Failed to load token symbol from chain. tokenAddress={}, blockNumber={}", tokenAddress, blockNumber, ex)
            null
        }
    }

    private fun persistMetadata(
        chain: String,
        tokenAddress: String,
        symbol: String,
        cached: TokenMetadata?,
    ): TokenMetadata {
        return tokenMetadataRepository.save(
            TokenMetadata(
                id = cached?.id,
                chain = chain,
                tokenAddress = tokenAddress,
                symbol = symbol,
                lastVerifiedAt = Instant.now(),
                createdAt = cached?.createdAt ?: Instant.now(),
                updatedAt = Instant.now()
            )
        )
    }

    private fun isStale(lastVerifiedAt: Instant): Boolean {
        return lastVerifiedAt.isBefore(Instant.now().minus(TOKEN_METADATA_STALE_AFTER))
    }

    private fun displayInfo(symbol: String, tokenAddress: String?, chain: String = ETHEREUM_CHAIN): TokenDisplayInfo {
        val chainLabel = when (chain) {
            ETHEREUM_CHAIN -> ETHEREUM_LABEL
            else -> chain.replaceFirstChar { it.uppercase() }
        }
        return TokenDisplayInfo(
            tokenSymbol = symbol,
            chain = chain,
            chainLabel = chainLabel,
            tokenAddress = tokenAddress,
            displayLabel = "$symbol ($chainLabel)"
        )
    }
}
