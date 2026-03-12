package com.example.ledger.domain.port

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.EventType

interface AccountingEventRepository {
    fun saveAll(events: List<AccountingEvent>): List<AccountingEvent>
    fun findByEventType(eventType: EventType): List<AccountingEvent>
    fun findByRawTransactionId(rawTransactionId: Long): List<AccountingEvent>
    fun findById(id: Long): AccountingEvent?
    fun save(event: AccountingEvent): AccountingEvent
}
