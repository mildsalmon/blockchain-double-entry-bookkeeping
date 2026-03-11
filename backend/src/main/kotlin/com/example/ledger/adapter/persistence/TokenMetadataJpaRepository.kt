package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.spring.SpringDataTokenMetadataRepository
import com.example.ledger.domain.model.TokenMetadata
import com.example.ledger.domain.port.TokenMetadataRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
class TokenMetadataJpaRepository(
    private val springDataTokenMetadataRepository: SpringDataTokenMetadataRepository
) : TokenMetadataRepository {
    override fun findByChainAndTokenAddress(chain: String, tokenAddress: String): TokenMetadata? {
        return springDataTokenMetadataRepository.findByChainAndTokenAddress(chain, tokenAddress)?.toDomain()
    }

    override fun save(metadata: TokenMetadata): TokenMetadata {
        return try {
            springDataTokenMetadataRepository.save(metadata.toEntity()).toDomain()
        } catch (ex: DataIntegrityViolationException) {
            springDataTokenMetadataRepository.findByChainAndTokenAddress(metadata.chain, metadata.tokenAddress)
                ?.toDomain()
                ?: throw ex
        }
    }
}
