package com.example.ledger.domain.port

import com.example.ledger.domain.model.AuditLogEntry

interface AuditLogRepository {
    fun save(entry: AuditLogEntry): AuditLogEntry
}
