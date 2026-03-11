package com.example.ledger.adapter.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "journal_lines")
class JournalLineEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    var journalEntry: JournalEntryEntity,
    @Column(name = "account_code", nullable = false)
    var accountCode: String,
    @Column(name = "debit_amount", nullable = false)
    var debitAmount: BigDecimal,
    @Column(name = "credit_amount", nullable = false)
    var creditAmount: BigDecimal,
    @Column(name = "token_symbol")
    var tokenSymbol: String? = null,
    @Column(name = "chain")
    var chain: String? = null,
    @Column(name = "token_address")
    var tokenAddress: String? = null,
    @Column(name = "token_quantity")
    var tokenQuantity: BigDecimal? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
