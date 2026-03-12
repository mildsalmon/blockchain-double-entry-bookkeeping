package com.example.ledger.domain.port

import com.example.ledger.domain.model.TokenMetadata

interface TokenMetadataRepository {
    fun findByChainAndTokenAddress(chain: String, tokenAddress: String): TokenMetadata?
    fun save(metadata: TokenMetadata): TokenMetadata
}
