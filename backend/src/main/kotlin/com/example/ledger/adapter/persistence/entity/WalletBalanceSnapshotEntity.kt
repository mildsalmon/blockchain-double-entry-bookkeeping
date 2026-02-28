package com.example.ledger.adapter.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "wallet_balance_snapshots")
data class WalletBalanceSnapshotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "wallet_id", nullable = false)
    val walletId: Long,
    @Column(name = "token_address", nullable = false)
    val tokenAddress: String,
    @Column(name = "token_symbol", nullable = false)
    val tokenSymbol: String,
    @Column(name = "balance_raw", nullable = false)
    val balanceRaw: BigDecimal,
    @Column(name = "cutoff_block", nullable = false)
    val cutoffBlock: Long,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
