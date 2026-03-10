package com.example.ledger.domain.model

import java.time.Instant

data class TokenMetadata(
    val id: Long? = null,
    val chain: String,
    val tokenAddress: String,
    val symbol: String,
    val lastVerifiedAt: Instant,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
