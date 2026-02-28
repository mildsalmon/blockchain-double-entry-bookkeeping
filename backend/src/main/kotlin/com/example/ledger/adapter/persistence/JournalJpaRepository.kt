package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.entity.JournalEntryEntity
import com.example.ledger.adapter.persistence.entity.JournalLineEntity
import com.example.ledger.adapter.persistence.spring.SpringDataJournalEntryRepository
import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.port.JournalRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JournalJpaRepository(
    private val springDataJournalEntryRepository: SpringDataJournalEntryRepository
) : JournalRepository {

    override fun save(entry: JournalEntry): JournalEntry {
        val saved = springDataJournalEntryRepository.save(entry.toEntity())
        return saved.toDomain()
    }

    override fun saveAll(entries: List<JournalEntry>): List<JournalEntry> {
        if (entries.isEmpty()) return emptyList()
        return springDataJournalEntryRepository.saveAll(entries.map { it.toEntity() }).map { it.toDomain() }
    }

    override fun existsByRawTransactionId(rawTransactionId: Long): Boolean {
        return springDataJournalEntryRepository.existsByRawTransactionId(rawTransactionId)
    }

    override fun findById(id: Long): JournalEntry? {
        return springDataJournalEntryRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByFilters(
        fromDate: Instant?,
        toDate: Instant?,
        status: JournalStatus?,
        accountCode: String?,
        walletAddress: String?,
        page: Int,
        size: Int
    ): List<JournalEntry> {
        val records = springDataJournalEntryRepository.findAllWithLines()
            .asSequence()
            .filter { fromDate == null || !it.entryDate.isBefore(fromDate) }
            .filter { toDate == null || !it.entryDate.isAfter(toDate) }
            .filter { status == null || it.status == status.name }
            .filter { accountCode == null || it.lines.any { line -> line.accountCode == accountCode } }
            .toList()

        val fromIndex = (page * size).coerceAtMost(records.size)
        val toIndex = ((page + 1) * size).coerceAtMost(records.size)
        return records.subList(fromIndex, toIndex).map { it.toDomain() }
    }

    private fun JournalEntry.toEntity(): JournalEntryEntity {
        val entity = JournalEntryEntity(
            id = id,
            accountingEventId = accountingEventId,
            rawTransactionId = rawTransactionId,
            entryDate = entryDate,
            description = description,
            status = status.name,
            memo = memo,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
        entity.replaceLines(lines.map { it.toEntity(entity) })
        return entity
    }

    private fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
        id = id,
        accountingEventId = accountingEventId,
        rawTransactionId = rawTransactionId,
        entryDate = entryDate,
        description = description,
        status = JournalStatus.valueOf(status),
        memo = memo,
        lines = lines.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun JournalLine.toEntity(parent: JournalEntryEntity): JournalLineEntity = JournalLineEntity(
        id = id,
        journalEntry = parent,
        accountCode = accountCode,
        debitAmount = debitAmount,
        creditAmount = creditAmount,
        tokenSymbol = tokenSymbol,
        tokenQuantity = tokenQuantity,
        createdAt = createdAt
    )

    private fun JournalLineEntity.toDomain(): JournalLine = JournalLine(
        id = id,
        accountCode = accountCode,
        debitAmount = debitAmount,
        creditAmount = creditAmount,
        tokenSymbol = tokenSymbol,
        tokenQuantity = tokenQuantity,
        createdAt = createdAt
    )
}
