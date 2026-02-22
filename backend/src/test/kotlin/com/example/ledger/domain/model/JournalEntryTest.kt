package com.example.ledger.domain.model

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JournalEntryTest {

    @Test
    fun `debit and credit totals must match`() {
        val unbalancedLines = listOf(
            JournalLine(
                accountCode = "자산:암호화폐:ETH",
                debitAmount = BigDecimal("1000"),
                creditAmount = BigDecimal.ZERO
            ),
            JournalLine(
                accountCode = "비용:가스비",
                debitAmount = BigDecimal.ZERO,
                creditAmount = BigDecimal("900")
            )
        )

        assertFailsWith<IllegalArgumentException> {
            JournalEntry(
                id = null,
                accountingEventId = null,
                rawTransactionId = 1L,
                entryDate = Instant.parse("2026-02-22T00:00:00Z"),
                description = "불균형 분개",
                status = JournalStatus.AUTO_CLASSIFIED,
                memo = null,
                lines = unbalancedLines,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }
    }

    @Test
    fun `approved journal cannot be edited`() {
        val entry = validEntry(status = JournalStatus.APPROVED)

        assertFailsWith<IllegalStateException> {
            entry.validateEditable()
        }
    }

    @Test
    fun `can update non approved journal`() {
        val entry = validEntry(status = JournalStatus.REVIEW_REQUIRED)

        val updated = entry.update(
            lines = listOf(
                JournalLine(
                    accountCode = "자산:암호화폐:ETH",
                    debitAmount = BigDecimal("1500"),
                    creditAmount = BigDecimal.ZERO
                ),
                JournalLine(
                    accountCode = "수익:에어드롭",
                    debitAmount = BigDecimal.ZERO,
                    creditAmount = BigDecimal("1500")
                )
            ),
            memo = "수정됨"
        )

        assertEquals("수정됨", updated.memo)
        assertEquals(BigDecimal("1500"), updated.lines.first().debitAmount)
    }

    private fun validEntry(status: JournalStatus): JournalEntry {
        return JournalEntry(
            id = null,
            accountingEventId = null,
            rawTransactionId = 1L,
            entryDate = Instant.parse("2026-02-22T00:00:00Z"),
            description = "정상 분개",
            status = status,
            memo = null,
            lines = listOf(
                JournalLine(
                    accountCode = "자산:암호화폐:ETH",
                    debitAmount = BigDecimal("1000"),
                    creditAmount = BigDecimal.ZERO
                ),
                JournalLine(
                    accountCode = "수익:에어드롭",
                    debitAmount = BigDecimal.ZERO,
                    creditAmount = BigDecimal("1000")
                )
            ),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
