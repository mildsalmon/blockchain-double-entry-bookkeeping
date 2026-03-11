package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.WalletEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import jakarta.persistence.LockModeType

interface SpringDataWalletRepository : JpaRepository<WalletEntity, Long> {
    fun findByAddress(address: String): WalletEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletEntity w WHERE w.address = :address")
    fun findByAddressForUpdate(@Param("address") address: String): WalletEntity?

    @Modifying
    @Transactional
    @Query("UPDATE WalletEntity w SET w.syncStatus = 'SYNCING', w.updatedAt = CURRENT_TIMESTAMP WHERE w.address = :address AND w.syncStatus <> 'SYNCING'")
    fun setStatusSyncingIfNotAlready(@Param("address") address: String): Int
}
