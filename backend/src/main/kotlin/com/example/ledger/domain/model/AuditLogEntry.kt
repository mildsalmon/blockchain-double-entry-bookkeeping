package com.example.ledger.domain.model

import java.time.Instant

data class AuditLogEntry(
    val id: Long? = null,
    val entityType: String,
    val entityId: String,
    val action: String,
    val oldValue: Map<String, Any?>? = null,
    val newValue: Map<String, Any?>? = null,
    val actor: String? = null,
    val createdAt: Instant = Instant.now()
)
