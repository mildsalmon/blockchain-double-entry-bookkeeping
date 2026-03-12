package com.example.ledger.adapter.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "wallet_tracked_tokens")
data class WalletTrackedTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "wallet_id", nullable = false)
    val walletId: Long,
    @Column(name = "token_address", nullable = false)
    val tokenAddress: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
