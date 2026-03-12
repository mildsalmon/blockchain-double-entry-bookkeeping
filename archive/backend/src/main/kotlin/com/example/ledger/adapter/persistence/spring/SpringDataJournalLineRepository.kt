package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.JournalLineEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataJournalLineRepository : JpaRepository<JournalLineEntity, Long> {
    fun existsByAccountCode(accountCode: String): Boolean
}
