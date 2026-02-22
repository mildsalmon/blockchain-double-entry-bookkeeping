package com.example.ledger.domain.service

interface AuditService {
    fun log(
        entityType: String,
        entityId: String,
        action: String,
        oldValue: Map<String, Any?>? = null,
        newValue: Map<String, Any?>? = null,
        actor: String? = null
    )
}
