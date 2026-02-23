package com.example.ledger.domain.model

import java.math.BigDecimal
import java.time.Instant

data class JournalEntry(
    val id: Long? = null,
    val accountingEventId: Long? = null,
    val rawTransactionId: Long,
    val entryDate: Instant,
    val description: String,
    val status: JournalStatus,
    val memo: String? = null,
    val lines: List<JournalLine>,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(lines.isNotEmpty()) { "Journal entry must have at least one line" }
        validateBalanced()
    }

    fun validateBalanced() {
        val debitTotal = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.debitAmount }
        val creditTotal = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.creditAmount }
        require(debitTotal.compareTo(creditTotal) == 0) {
            "Journal entry must be balanced. debit=$debitTotal credit=$creditTotal"
        }
    }

    fun validateEditable() {
        check(status != JournalStatus.APPROVED) { "Approved journal entries cannot be edited" }
    }

    fun update(lines: List<JournalLine>, memo: String?): JournalEntry {
        validateEditable()
        validateTokenMetadataImmutable(lines)
        return copy(
            lines = lines,
            memo = memo,
            updatedAt = Instant.now()
        ).also { it.validateBalanced() }
    }

    fun approve(): JournalEntry {
        validateEditable()
        return copy(status = JournalStatus.APPROVED, updatedAt = Instant.now())
    }

    private fun validateTokenMetadataImmutable(updatedLines: List<JournalLine>) {
        require(updatedLines.size == lines.size) {
            "Line count cannot be changed for existing journal entries"
        }
        updatedLines.zip(lines).forEachIndexed { index, (updated, existing) ->
            val sameTokenSymbol = updated.tokenSymbol == existing.tokenSymbol
            val sameTokenQuantity = when {
                updated.tokenQuantity == null && existing.tokenQuantity == null -> true
                updated.tokenQuantity != null && existing.tokenQuantity != null ->
                    updated.tokenQuantity.compareTo(existing.tokenQuantity) == 0
                else -> false
            }
            require(sameTokenSymbol && sameTokenQuantity) {
                "Token metadata is immutable on line $index. Re-run pipeline for quantity changes."
            }
        }
    }
}
