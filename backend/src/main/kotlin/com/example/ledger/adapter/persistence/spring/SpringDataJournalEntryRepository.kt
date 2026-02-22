package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.JournalEntryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SpringDataJournalEntryRepository : JpaRepository<JournalEntryEntity, Long> {

    @Query(
        """
        select distinct je from JournalEntryEntity je
        left join fetch je.lines jl
        order by je.entryDate desc
        """
    )
    fun findAllWithLines(): List<JournalEntryEntity>
}
