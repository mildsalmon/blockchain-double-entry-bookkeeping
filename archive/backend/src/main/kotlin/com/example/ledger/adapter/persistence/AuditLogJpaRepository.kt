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

    override fun findLatest(entityType: String, entityId: String, action: String?): AuditLogEntry? {
        val entity = if (action.isNullOrBlank()) {
            springDataAuditLogRepository.findTopByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
        } else {
            springDataAuditLogRepository.findTopByEntityTypeAndEntityIdAndActionOrderByCreatedAtDesc(entityType, entityId, action)
        } ?: return null

        @Suppress("UNCHECKED_CAST")
        val oldValue = entity.oldValue?.let { objectMapper.convertValue(it, Map::class.java) as Map<String, Any?> }
        @Suppress("UNCHECKED_CAST")
        val newValue = entity.newValue?.let { objectMapper.convertValue(it, Map::class.java) as Map<String, Any?> }

        return AuditLogEntry(
            id = entity.id,
            entityType = entity.entityType,
            entityId = entity.entityId,
            action = entity.action,
            oldValue = oldValue,
            newValue = newValue,
            actor = entity.actor,
            createdAt = entity.createdAt
        )
    }
}
