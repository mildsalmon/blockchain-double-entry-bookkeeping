package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.spring.SpringDataAccountingEventRepository
import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.port.AccountingEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Repository

@Repository
class AccountingEventJpaRepository(
    private val springDataAccountingEventRepository: SpringDataAccountingEventRepository,
    private val objectMapper: ObjectMapper
) : AccountingEventRepository {
    override fun saveAll(events: List<AccountingEvent>): List<AccountingEvent> {
        if (events.isEmpty()) return emptyList()
        return springDataAccountingEventRepository
            .saveAll(events.map { it.toEntity(objectMapper) })
            .map { it.toDomain(objectMapper) }
    }

    override fun findByEventType(eventType: EventType): List<AccountingEvent> {
        return springDataAccountingEventRepository.findByEventType(eventType.name).map { it.toDomain(objectMapper) }
    }

    override fun findByRawTransactionId(rawTransactionId: Long): List<AccountingEvent> {
        return springDataAccountingEventRepository.findByRawTransactionId(rawTransactionId).map { it.toDomain(objectMapper) }
    }

    override fun findById(id: Long): AccountingEvent? {
        return springDataAccountingEventRepository.findById(id).orElse(null)?.toDomain(objectMapper)
    }

    override fun save(event: AccountingEvent): AccountingEvent {
        return springDataAccountingEventRepository.save(event.toEntity(objectMapper)).toDomain(objectMapper)
    }
}
