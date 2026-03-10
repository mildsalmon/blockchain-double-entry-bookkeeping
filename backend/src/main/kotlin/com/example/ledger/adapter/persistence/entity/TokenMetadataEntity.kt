package com.example.ledger.adapter.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "token_metadata")
data class TokenMetadataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "chain", nullable = false)
    val chain: String,
    @Column(name = "token_address", nullable = false)
    val tokenAddress: String,
    @Column(name = "symbol", nullable = false)
    val symbol: String,
    @Column(name = "last_verified_at", nullable = false)
    val lastVerifiedAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
