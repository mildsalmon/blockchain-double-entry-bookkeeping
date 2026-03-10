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

    @Test
    fun `cannot change token metadata when updating journal`() {
        val entry = JournalEntry(
            rawTransactionId = 1L,
            entryDate = Instant.parse("2026-02-22T00:00:00Z"),
            description = "토큰 메타데이터 검증",
            status = JournalStatus.REVIEW_REQUIRED,
            lines = listOf(
                JournalLine(
                    accountCode = "자산:암호화폐:ETH",
                    debitAmount = BigDecimal("1000"),
                    creditAmount = BigDecimal.ZERO,
                    tokenSymbol = "ETH",
                    tokenQuantity = BigDecimal("0.25")
                ),
                JournalLine(
                    accountCode = "수익:에어드롭",
                    debitAmount = BigDecimal.ZERO,
                    creditAmount = BigDecimal("1000"),
                    tokenSymbol = "ETH",
                    tokenQuantity = BigDecimal("0.25")
                )
            )
        )

        assertFailsWith<IllegalArgumentException> {
            entry.update(
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ETH",
                        debitAmount = BigDecimal("1000"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("0.30")
                    ),
                    JournalLine(
                        accountCode = "수익:에어드롭",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("1000"),
                        tokenSymbol = "ETH",
                        tokenQuantity = BigDecimal("0.30")
                    )
                ),
                memo = "토큰 수량 변경 시도"
            )
        }
    }

    @Test
    fun `cannot change token chain or address when updating journal`() {
        val entry = JournalEntry(
            rawTransactionId = 1L,
            entryDate = Instant.parse("2026-02-22T00:00:00Z"),
            description = "토큰 주소 검증",
            status = JournalStatus.REVIEW_REQUIRED,
            lines = listOf(
                JournalLine(
                    accountCode = "자산:암호화폐:ERC20:USDC@0x1111111111111111111111111111111111111111",
                    debitAmount = BigDecimal("1000"),
                    creditAmount = BigDecimal.ZERO,
                    tokenSymbol = "USDC",
                    chain = "ETHEREUM",
                    tokenAddress = "0x1111111111111111111111111111111111111111",
                    tokenQuantity = BigDecimal("1.0")
                ),
                JournalLine(
                    accountCode = "자산:외부",
                    debitAmount = BigDecimal.ZERO,
                    creditAmount = BigDecimal("1000"),
                    tokenSymbol = "USDC",
                    chain = "ETHEREUM",
                    tokenAddress = "0x1111111111111111111111111111111111111111",
                    tokenQuantity = BigDecimal("1.0")
                )
            )
        )

        assertFailsWith<IllegalArgumentException> {
            entry.update(
                lines = listOf(
                    JournalLine(
                        accountCode = "자산:암호화폐:ERC20:USDC@0x2222222222222222222222222222222222222222",
                        debitAmount = BigDecimal("1000"),
                        creditAmount = BigDecimal.ZERO,
                        tokenSymbol = "USDC",
                        chain = "ethereum",
                        tokenAddress = "0x2222222222222222222222222222222222222222",
                        tokenQuantity = BigDecimal("1.0")
                    ),
                    JournalLine(
                        accountCode = "자산:외부",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = BigDecimal("1000"),
                        tokenSymbol = "USDC",
                        chain = "ETHEREUM",
                        tokenAddress = "0x1111111111111111111111111111111111111111",
                        tokenQuantity = BigDecimal("1.0")
                    )
                ),
                memo = "토큰 주소 변경 시도"
            )
        }
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
