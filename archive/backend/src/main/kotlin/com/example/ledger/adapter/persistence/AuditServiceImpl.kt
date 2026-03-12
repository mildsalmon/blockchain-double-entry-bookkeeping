package com.example.ledger.adapter.persistence

import com.example.ledger.domain.model.AuditLogEntry
import com.example.ledger.domain.port.AuditLogRepository
import com.example.ledger.domain.service.AuditService
import org.springframework.stereotype.Service

@Service
class AuditServiceImpl(
    private val auditLogRepository: AuditLogRepository
) : AuditService {
    override fun log(
        entityType: String,
        entityId: String,
        action: String,
        oldValue: Map<String, Any?>?,
        newValue: Map<String, Any?>?,
        actor: String?
    ) {
        auditLogRepository.save(
            AuditLogEntry(
                entityType = entityType,
                entityId = entityId,
                action = action,
                oldValue = oldValue,
                newValue = newValue,
                actor = actor
            )
        )
    }
}
