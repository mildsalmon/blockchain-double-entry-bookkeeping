package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.WalletEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataWalletRepository : JpaRepository<WalletEntity, Long> {
    fun findByAddress(address: String): WalletEntity?
}
