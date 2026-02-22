package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.entity.AuditLogEntity
import com.example.ledger.adapter.persistence.spring.SpringDataAuditLogRepository
import com.example.ledger.domain.model.AuditLogEntry
import com.example.ledger.domain.port.AuditLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Repository

@Repository
class AuditLogJpaRepository(
    private val springDataAuditLogRepository: SpringDataAuditLogRepository,
    private val objectMapper: ObjectMapper
) : AuditLogRepository {
    override fun save(entry: AuditLogEntry): AuditLogEntry {
        val saved = springDataAuditLogRepository.save(
            AuditLogEntity(
                id = entry.id,
                entityType = entry.entityType,
                entityId = entry.entityId,
                action = entry.action,
                oldValue = entry.oldValue?.let { objectMapper.valueToTree(it) },
                newValue = entry.newValue?.let { objectMapper.valueToTree(it) },
                actor = entry.actor,
                createdAt = entry.createdAt
            )
        )

        return entry.copy(id = saved.id)
    }
}
