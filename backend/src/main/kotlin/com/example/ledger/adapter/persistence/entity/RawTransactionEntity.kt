package com.example.ledger.adapter.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "raw_transactions")
data class RawTransactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "wallet_address", nullable = false)
    val walletAddress: String,
    @Column(name = "tx_hash", nullable = false, unique = true)
    val txHash: String,
    @Column(name = "block_number", nullable = false)
    val blockNumber: Long,
    @Column(name = "tx_index")
    val txIndex: Int? = null,
    @Column(name = "block_timestamp", nullable = false)
    val blockTimestamp: Instant,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", nullable = false, columnDefinition = "jsonb")
    val rawData: JsonNode,
    @Column(name = "tx_status", nullable = false)
    val txStatus: Short,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
