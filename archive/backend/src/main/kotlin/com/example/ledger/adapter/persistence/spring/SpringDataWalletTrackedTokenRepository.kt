package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.WalletTrackedTokenEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataWalletTrackedTokenRepository : JpaRepository<WalletTrackedTokenEntity, Long> {
    fun findByWalletIdOrderByTokenAddressAsc(walletId: Long): List<WalletTrackedTokenEntity>
    fun deleteByWalletId(walletId: Long)
}
