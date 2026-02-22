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
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "accounting_events")
data class AccountingEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "raw_transaction_id", nullable = false)
    val rawTransactionId: Long,
    @Column(name = "event_type", nullable = false)
    val eventType: String,
    @Column(name = "classifier_id", nullable = false)
    val classifierId: String,
    @Column(name = "token_address")
    val tokenAddress: String? = null,
    @Column(name = "token_symbol")
    val tokenSymbol: String? = null,
    @Column(name = "amount_raw", nullable = false)
    val amountRaw: BigDecimal,
    @Column(name = "amount_decimal", nullable = false)
    val amountDecimal: BigDecimal,
    @Column(name = "counterparty")
    val counterparty: String? = null,
    @Column(name = "price_krw")
    val priceKrw: BigDecimal? = null,
    @Column(name = "price_source")
    val priceSource: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    val metadata: JsonNode? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
