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
@Table(name = "audit_log")
data class AuditLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "entity_type", nullable = false)
    val entityType: String,
    @Column(name = "entity_id", nullable = false)
    val entityId: String,
    @Column(name = "action", nullable = false)
    val action: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    val oldValue: JsonNode? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    val newValue: JsonNode? = null,
    @Column(name = "actor")
    val actor: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
