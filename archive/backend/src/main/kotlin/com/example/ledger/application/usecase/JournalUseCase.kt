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
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.RawTransactionRepository
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.service.LedgerService
import com.example.ledger.domain.service.TokenMetadataService
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.time.ZoneOffset

@Service
class JournalUseCase(
    private val journalRepository: JournalRepository,
    private val rawTransactionRepository: RawTransactionRepository,
    private val accountingEventRepository: AccountingEventRepository,
    private val ledgerService: LedgerService,
    private val tokenMetadataService: TokenMetadataService,
    private val pricePort: PricePort
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
        if (eventType == EventType.SWAP) {
            throw IllegalArgumentException("Manual SWAP classification is not supported")
        }
        val rawTx = rawTransactionRepository.findById(existing.rawTransactionId)
            ?: throw IllegalArgumentException("Raw transaction not found: ${existing.rawTransactionId}")
        // TODO(multichain-fees): FEE currently assumes Ethereum-native gas semantics.
        // When multiple networks are supported, validate manual fee classification against the tx chain's native fee asset.
        val normalizedTokenSymbol = tokenMetadataService.normalizeSymbol(tokenSymbol)
            ?: throw IllegalArgumentException("Invalid token symbol: $tokenSymbol")
        val normalizedTokenAddress = when {
            tokenAddress.isNullOrBlank() -> null
            else -> tokenMetadataService.normalizeContractAddress(tokenAddress)
                ?: throw IllegalArgumentException("Invalid token address: $tokenAddress")
        }
        val canonicalTokenSymbol = if (normalizedTokenAddress == null) {
            if (normalizedTokenSymbol != "ETH") {
                throw IllegalArgumentException("Contract address is required for non-ETH manual classifications")
            }
            "ETH"
        } else {
            val resolved = tokenMetadataService.resolveForWrite(
                tokenAddress = normalizedTokenAddress,
                fallbackSymbol = null,
                blockNumber = null
            )
            if (resolved.tokenSymbol == TokenMetadataService.ERR_SYMBOL) {
                throw IllegalArgumentException("Unable to resolve token symbol for token address: $normalizedTokenAddress")
            }
            resolved.tokenSymbol
        }
        val repriced = pricePort.getPrice(
            normalizedTokenAddress,
            canonicalTokenSymbol,
            rawTx.blockTimestamp.atOffset(ZoneOffset.UTC).toLocalDate()
        )

        val updatedEvent = accountingEventRepository.save(
            existing.copy(
                eventType = eventType,
                classifierId = "MANUAL",
                tokenSymbol = canonicalTokenSymbol,
                tokenAddress = normalizedTokenAddress,
                amountDecimal = amountDecimal,
                amountRaw = amountDecimal.toBigInteger(),
                priceKrw = repriced.priceKrw,
                priceSource = repriced.source.takeIf { it != PriceSource.UNKNOWN } ?: PriceSource.UNKNOWN
            )
        )

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
                if (it.tokenQuantity == null && it.tokenSymbol.isNullOrBlank() && it.tokenAddress.isNullOrBlank() && it.chain.isNullOrBlank()) {
                    return@map com.example.ledger.application.dto.JournalLineResponse(
                        id = it.id,
                        accountCode = it.accountCode,
                        debitAmount = it.debitAmount,
                        creditAmount = it.creditAmount,
                        tokenSymbol = null,
                        chain = null,
                        chainLabel = "",
                        tokenAddress = null,
                        displayLabel = null,
                        tokenQuantity = null
                    )
                }
                val tokenDisplay = tokenMetadataService.resolveForRead(
                    tokenAddress = it.tokenAddress,
                    fallbackSymbol = it.tokenSymbol,
                    chain = it.chain
                )
                com.example.ledger.application.dto.JournalLineResponse(
                    id = it.id,
                    accountCode = it.accountCode,
                    debitAmount = it.debitAmount,
                    creditAmount = it.creditAmount,
                    tokenSymbol = tokenDisplay.tokenSymbol,
                    chain = it.chain,
                    chainLabel = tokenDisplay.chainLabel,
                    tokenAddress = it.tokenAddress,
                    displayLabel = it.tokenQuantity?.let { _ -> tokenDisplay.displayLabel },
                    tokenQuantity = it.tokenQuantity
                )
            }
        )
    }
}
