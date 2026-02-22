package com.example.ledger.domain.port

import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalStatus
import java.time.Instant

interface JournalRepository {
    fun save(entry: JournalEntry): JournalEntry
    fun saveAll(entries: List<JournalEntry>): List<JournalEntry>
    fun findById(id: Long): JournalEntry?
    fun findByFilters(
        fromDate: Instant? = null,
        toDate: Instant? = null,
        status: JournalStatus? = null,
        accountCode: String? = null,
        walletAddress: String? = null,
        page: Int = 0,
        size: Int = 50
    ): List<JournalEntry>
}
