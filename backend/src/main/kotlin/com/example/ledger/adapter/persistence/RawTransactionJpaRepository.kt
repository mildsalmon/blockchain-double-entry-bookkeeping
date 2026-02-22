package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.spring.SpringDataRawTransactionRepository
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.port.RawTransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Repository

@Repository
class RawTransactionJpaRepository(
    private val springDataRawTransactionRepository: SpringDataRawTransactionRepository,
    private val objectMapper: ObjectMapper
) : RawTransactionRepository {
    override fun saveAll(transactions: List<RawTransaction>): List<RawTransaction> {
        if (transactions.isEmpty()) return emptyList()
        val entities = transactions
            .filterNot { springDataRawTransactionRepository.existsByTxHash(it.txHash) }
            .map { it.toEntity(objectMapper) }

        if (entities.isEmpty()) return emptyList()
        return springDataRawTransactionRepository.saveAll(entities).map { it.toDomain(objectMapper) }
    }

    override fun findByWalletAddress(walletAddress: String): List<RawTransaction> {
        return springDataRawTransactionRepository
            .findByWalletAddressOrderByBlockNumberAscTxIndexAsc(walletAddress)
            .map { it.toDomain(objectMapper) }
    }

    override fun existsByTxHash(txHash: String): Boolean {
        return springDataRawTransactionRepository.existsByTxHash(txHash)
    }

    override fun findById(id: Long): RawTransaction? {
        return springDataRawTransactionRepository.findById(id).orElse(null)?.toDomain(objectMapper)
    }
}
