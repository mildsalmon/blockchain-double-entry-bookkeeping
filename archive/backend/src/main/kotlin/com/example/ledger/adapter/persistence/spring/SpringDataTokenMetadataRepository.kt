package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.TokenMetadataEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataTokenMetadataRepository : JpaRepository<TokenMetadataEntity, Long> {
    fun findByChainAndTokenAddress(chain: String, tokenAddress: String): TokenMetadataEntity?
}
