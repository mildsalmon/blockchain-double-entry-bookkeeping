package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.RawTransactionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataRawTransactionRepository : JpaRepository<RawTransactionEntity, Long> {
    fun findByWalletAddressOrderByBlockNumberAscTxIndexAsc(walletAddress: String): List<RawTransactionEntity>
    fun existsByTxHash(txHash: String): Boolean
}
