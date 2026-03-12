package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.AccountingEventEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataAccountingEventRepository : JpaRepository<AccountingEventEntity, Long> {
    fun findByEventType(eventType: String): List<AccountingEventEntity>
    fun findByRawTransactionId(rawTransactionId: Long): List<AccountingEventEntity>
}
