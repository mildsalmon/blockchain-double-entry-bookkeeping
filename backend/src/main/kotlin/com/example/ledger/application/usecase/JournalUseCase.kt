package com.example.ledger.application.usecase

import com.example.ledger.application.dto.JournalDetailResponse
import com.example.ledger.application.dto.JournalFilterRequest
import com.example.ledger.application.dto.JournalResponse
import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.port.AccountingEventRepository
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.RawTransactionRepository
import com.example.ledger.domain.service.LedgerService
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.ZoneOffset

@Service
class JournalUseCase(
    private val journalRepository: JournalRepository,
    private val rawTransactionRepository: RawTransactionRepository,
    private val accountingEventRepository: AccountingEventRepository,
    private val ledgerService: LedgerService
) {
    fun list(filter: JournalFilterRequest): List<JournalResponse> {
        val journals = journalRepository.findByFilters(
            fromDate = filter.fromDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC),
            toDate = filter.toDate?.plusDays(1)?.atStartOfDay()?.minusNanos(1)?.toInstant(ZoneOffset.UTC),
            status = filter.status,
            accountCode = filter.accountCode,
            walletAddress = filter.walletAddress,
            page = filter.page,
            size = filter.size
        )

        return journals.map { it.toResponse() }
    }

    fun get(id: Long): JournalDetailResponse {
        val journal = journalRepository.findById(id)
            ?: throw IllegalArgumentException("Journal not found: $id")
        val rawTx = rawTransactionRepository.findById(journal.rawTransactionId)
        val event = journal.accountingEventId?.let { accountingEventRepository.findById(it) }

        return JournalDetailResponse(
            journal = journal.toResponse(),
            txHash = rawTx?.txHash,
            classifierId = event?.classifierId,
            priceSource = event?.priceSource
        )
    }

    fun update(id: Long, lines: List<JournalLine>, memo: String?): JournalResponse {
        return ledgerService.updateEntry(id, lines, memo).toResponse()
    }

    fun approve(id: Long): JournalResponse {
        return ledgerService.approveEntry(id).toResponse()
    }

    fun bulkApprove(ids: List<Long>): List<JournalResponse> {
        return ids.map { approve(it) }
    }

    fun listUnclassified(): List<AccountingEvent> {
        return accountingEventRepository.findByEventType(EventType.UNCLASSIFIED)
    }

    fun manualClassify(eventId: Long, eventType: EventType, tokenSymbol: String, amountDecimal: java.math.BigDecimal, tokenAddress: String?): JournalResponse {
        val existing = accountingEventRepository.findById(eventId)
            ?: throw IllegalArgumentException("Accounting event not found: $eventId")

        val updatedEvent = accountingEventRepository.save(
            existing.copy(
                eventType = eventType,
                classifierId = "MANUAL",
                tokenSymbol = tokenSymbol,
                tokenAddress = tokenAddress,
                amountDecimal = amountDecimal,
                amountRaw = amountDecimal.toBigInteger(),
                priceKrw = existing.priceKrw ?: java.math.BigDecimal.ZERO,
                priceSource = existing.priceSource
            )
        )

        val rawTx = rawTransactionRepository.findById(updatedEvent.rawTransactionId)
            ?: throw IllegalArgumentException("Raw transaction not found: ${updatedEvent.rawTransactionId}")

        val entries = ledgerService.generateEntries(
            walletAddress = rawTx.walletAddress,
            rawTransactionId = rawTx.id ?: throw IllegalStateException("Raw transaction id is null"),
            events = listOf(updatedEvent),
            entryDate = rawTx.blockTimestamp
        )

        val saved = entries.firstOrNull()
            ?: throw IllegalStateException("No journal generated for manual classification")

        val manual = journalRepository.save(saved.copy(status = JournalStatus.MANUAL_CLASSIFIED))
        return manual.toResponse()
    }

    private fun JournalEntry.toResponse(): JournalResponse {
        return JournalResponse(
            id = id,
            rawTransactionId = rawTransactionId,
            entryDate = entryDate,
            description = description,
            status = status,
            memo = memo,
            lines = lines.map {
                com.example.ledger.application.dto.JournalLineResponse(
                    id = it.id,
                    accountCode = it.accountCode,
                    debitAmount = it.debitAmount,
                    creditAmount = it.creditAmount,
                    tokenSymbol = it.tokenSymbol,
                    tokenQuantity = it.tokenQuantity
                )
            }
        )
    }
}
