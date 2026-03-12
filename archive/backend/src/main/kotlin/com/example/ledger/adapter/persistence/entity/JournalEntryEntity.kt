package com.example.ledger.adapter.persistence.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "journal_entries")
class JournalEntryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "accounting_event_id")
    var accountingEventId: Long? = null,
    @Column(name = "raw_transaction_id")
    var rawTransactionId: Long,
    @Column(name = "entry_date", nullable = false)
    var entryDate: Instant,
    @Column(name = "description", nullable = false)
    var description: String,
    @Column(name = "status", nullable = false)
    var status: String,
    @Column(name = "memo")
    var memo: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "journalEntry", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var lines: MutableList<JournalLineEntity> = mutableListOf()
) {
    fun replaceLines(newLines: List<JournalLineEntity>) {
        lines.clear()
        lines.addAll(newLines)
        lines.forEach { it.journalEntry = this }
    }
}
