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
@Table(name = "cost_basis_lots")
data class CostBasisLotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "wallet_address", nullable = false)
    val walletAddress: String,
    @Column(name = "token_symbol", nullable = false)
    val tokenSymbol: String,
    @Column(name = "acquisition_date", nullable = false)
    val acquisitionDate: Instant,
    @Column(name = "quantity", nullable = false)
    val quantity: BigDecimal,
    @Column(name = "remaining_qty", nullable = false)
    val remainingQty: BigDecimal,
    @Column(name = "unit_cost_krw", nullable = false)
    val unitCostKrw: BigDecimal,
    @Column(name = "raw_transaction_id")
    val rawTransactionId: Long? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
