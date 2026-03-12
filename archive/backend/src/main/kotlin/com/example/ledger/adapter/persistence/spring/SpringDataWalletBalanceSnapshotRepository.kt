package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.WalletBalanceSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataWalletBalanceSnapshotRepository : JpaRepository<WalletBalanceSnapshotEntity, Long> {
    fun findByWalletIdOrderByIdAsc(walletId: Long): List<WalletBalanceSnapshotEntity>
    fun deleteByWalletId(walletId: Long)
}
