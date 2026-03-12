package com.example.ledger.adapter.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "price_cache")
data class PriceCacheEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "token_address")
    val tokenAddress: String? = null,
    @Column(name = "token_symbol", nullable = false)
    val tokenSymbol: String,
    @Column(name = "price_date", nullable = false)
    val priceDate: LocalDate,
    @Column(name = "price_krw", nullable = false)
    val priceKrw: BigDecimal,
    @Column(name = "source", nullable = false)
    val source: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
