package com.example.ledger.domain.service

import com.example.ledger.config.SerializableTx
import com.example.ledger.domain.model.Account
import com.example.ledger.domain.model.AccountCategory
import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.port.AccountRepository
import com.example.ledger.domain.port.JournalRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant

@Service
class LedgerService(
    private val journalRepository: JournalRepository,
    private val accountRepository: AccountRepository,
    private val fifoService: FifoService,
    private val gainLossService: GainLossService,
    private val auditService: AuditService
) {
    companion object {
        private const val ETH_ACCOUNT = "자산:암호화폐:ETH"
        private const val ERC20_PREFIX = "자산:암호화폐:ERC20:"
        private const val GAS_FEE_ACCOUNT = "비용:가스비"
        private const val TRADE_FEE_ACCOUNT = "비용:거래수수료"
        private const val REALIZED_GAIN_ACCOUNT = "수익:실현이익"
        private const val REALIZED_LOSS_ACCOUNT = "비용:실현손실"
        private const val AIRDROP_ACCOUNT = "수익:에어드롭"
        private const val UNSPECIFIED_INCOME_ACCOUNT = "수익:미지정수입"
    }

    @SerializableTx
    fun generateEntries(
        walletAddress: String,
        rawTransactionId: Long,
        events: List<AccountingEvent>,
        entryDate: Instant
    ): List<JournalEntry> {
        return events.mapNotNull { event ->
            if (event.eventType == EventType.UNCLASSIFIED) {
                return@mapNotNull null
            }
            val entry = createEntry(walletAddress, rawTransactionId, event, entryDate)
            val saved = journalRepository.save(entry)
            auditService.log(
                entityType = "JOURNAL_ENTRY",
                entityId = saved.id?.toString() ?: "",
                action = "CREATE",
                oldValue = null,
                newValue = mapOf("status" to saved.status.name, "rawTransactionId" to saved.rawTransactionId)
            )
            saved
        }
    }

    fun updateEntry(id: Long, lines: List<JournalLine>, memo: String?): JournalEntry {
        val existing = journalRepository.findById(id)
            ?: throw IllegalArgumentException("Journal entry not found: $id")

        val updated = existing.update(lines, memo)
        val saved = journalRepository.save(updated)

        auditService.log(
            entityType = "JOURNAL_ENTRY",
            entityId = id.toString(),
            action = "UPDATE",
            oldValue = mapOf("memo" to existing.memo, "status" to existing.status.name),
            newValue = mapOf("memo" to saved.memo, "status" to saved.status.name)
        )

        return saved
    }

    fun approveEntry(id: Long): JournalEntry {
        val existing = journalRepository.findById(id)
            ?: throw IllegalArgumentException("Journal entry not found: $id")

        val approved = existing.approve()
        val saved = journalRepository.save(approved)

        auditService.log(
            entityType = "JOURNAL_ENTRY",
            entityId = id.toString(),
            action = "APPROVE",
            oldValue = mapOf("status" to existing.status.name),
            newValue = mapOf("status" to saved.status.name)
        )

        return saved
    }

    private fun createEntry(
        walletAddress: String,
        rawTransactionId: Long,
        event: AccountingEvent,
        entryDate: Instant
    ): JournalEntry {
        val amountKrw = (event.amountDecimal * (event.priceKrw ?: BigDecimal.ZERO))
            .roundMoney()

        return when (event.eventType) {
            EventType.INCOMING -> createIncomingEntry(
                walletAddress = walletAddress,
                rawTransactionId = rawTransactionId,
                event = event,
                entryDate = entryDate,
                amountKrw = amountKrw,
                offsetAccountCode = UNSPECIFIED_INCOME_ACCOUNT
            )
            EventType.OUTGOING -> createOutgoingEntry(walletAddress, rawTransactionId, event, entryDate, amountKrw)
            EventType.FEE -> createFeeEntry(walletAddress, rawTransactionId, event, entryDate, amountKrw)
            EventType.SWAP -> createSwapEntry(walletAddress, rawTransactionId, event, entryDate, amountKrw)
            EventType.MANUAL_CLASSIFIED -> createIncomingEntry(
                walletAddress = walletAddress,
                rawTransactionId = rawTransactionId,
                event = event,
                entryDate = entryDate,
                amountKrw = amountKrw,
                offsetAccountCode = AIRDROP_ACCOUNT
            )
            EventType.UNCLASSIFIED -> throw IllegalArgumentException("UNCLASSIFIED event cannot generate journal entry")
        }
    }

    private fun createIncomingEntry(
        walletAddress: String,
        rawTransactionId: Long,
        event: AccountingEvent,
        entryDate: Instant,
        amountKrw: BigDecimal,
        offsetAccountCode: String
    ): JournalEntry {
        val assetAccountCode = resolveAssetAccountCode(event)
        fifoService.addLot(
            walletAddress = walletAddress,
            tokenSymbol = event.tokenSymbol ?: "ETH",
            quantity = event.amountDecimal,
            unitCostKrw = event.priceKrw ?: BigDecimal.ZERO,
            rawTransactionId = rawTransactionId,
            acquisitionDate = entryDate
        )

        return JournalEntry(
            accountingEventId = event.id,
            rawTransactionId = rawTransactionId,
            entryDate = entryDate,
            description = "Incoming ${event.tokenSymbol ?: "ETH"}",
            status = JournalStatus.AUTO_CLASSIFIED,
            lines = listOf(
                line(assetAccountCode, debit = amountKrw),
                line(offsetAccountCode, credit = amountKrw)
            )
        )
    }

    private fun createOutgoingEntry(
        walletAddress: String,
        rawTransactionId: Long,
        event: AccountingEvent,
        entryDate: Instant,
        proceedsKrw: BigDecimal
    ): JournalEntry {
        val assetAccountCode = resolveAssetAccountCode(event)
        val fifo = fifoService.consume(walletAddress, event.tokenSymbol ?: "ETH", event.amountDecimal)
        val gainLoss = gainLossService.calculate(proceedsKrw, fifo.totalCostKrw)

        val lines = mutableListOf(
            line(TRADE_FEE_ACCOUNT, debit = proceedsKrw),
            line(assetAccountCode, credit = fifo.totalCostKrw)
        )
        lines += gainLossLines(gainLoss)

        return JournalEntry(
            accountingEventId = event.id,
            rawTransactionId = rawTransactionId,
            entryDate = entryDate,
            description = "Outgoing ${event.tokenSymbol ?: "ETH"}",
            status = JournalStatus.AUTO_CLASSIFIED,
            lines = lines
        )
    }

    private fun createFeeEntry(
        walletAddress: String,
        rawTransactionId: Long,
        event: AccountingEvent,
        entryDate: Instant,
        proceedsKrw: BigDecimal
    ): JournalEntry {
        val assetAccountCode = ETH_ACCOUNT
        val fifo = fifoService.consume(walletAddress, "ETH", event.amountDecimal)
        val gainLoss = gainLossService.calculate(proceedsKrw, fifo.totalCostKrw)

        val lines = mutableListOf(
            line(GAS_FEE_ACCOUNT, debit = proceedsKrw),
            line(assetAccountCode, credit = fifo.totalCostKrw)
        )
        lines += gainLossLines(gainLoss)

        return JournalEntry(
            accountingEventId = event.id,
            rawTransactionId = rawTransactionId,
            entryDate = entryDate,
            description = "Gas fee",
            status = JournalStatus.AUTO_CLASSIFIED,
            lines = lines
        )
    }

    private fun createSwapEntry(
        walletAddress: String,
        rawTransactionId: Long,
        event: AccountingEvent,
        entryDate: Instant,
        proceedsKrw: BigDecimal
    ): JournalEntry {
        val tokenInSymbol = event.tokenSymbol ?: "ETH"
        val tokenOutSymbol = event.metadata["tokenOutSymbol"]?.toString() ?: "ETH"
        val tokenOutAmount = event.metadata["amountOut"]?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        val tokenInAccount = resolveAssetAccountCode(event)
        val tokenOutAccount = resolveAssetAccountCode(
            event.copy(
                tokenSymbol = tokenOutSymbol,
                tokenAddress = event.metadata["tokenOutAddress"]?.toString()
            )
        )

        val fifo = fifoService.consume(walletAddress, tokenInSymbol, event.amountDecimal)
        val gainLoss = gainLossService.calculate(proceedsKrw, fifo.totalCostKrw)

        if (tokenOutAmount > BigDecimal.ZERO) {
            val unitCost = if (tokenOutAmount == BigDecimal.ZERO) BigDecimal.ZERO else proceedsKrw.divide(tokenOutAmount, MathContext.DECIMAL128)
            fifoService.addLot(
                walletAddress = walletAddress,
                tokenSymbol = tokenOutSymbol,
                quantity = tokenOutAmount,
                unitCostKrw = unitCost,
                rawTransactionId = rawTransactionId,
                acquisitionDate = entryDate
            )
        }

        val lines = mutableListOf(
            line(tokenOutAccount, debit = proceedsKrw, tokenSymbol = tokenOutSymbol, tokenQuantity = tokenOutAmount),
            line(tokenInAccount, credit = fifo.totalCostKrw, tokenSymbol = tokenInSymbol, tokenQuantity = event.amountDecimal)
        )
        lines += gainLossLines(gainLoss)

        return JournalEntry(
            accountingEventId = event.id,
            rawTransactionId = rawTransactionId,
            entryDate = entryDate,
            description = "Swap $tokenInSymbol -> $tokenOutSymbol",
            status = JournalStatus.AUTO_CLASSIFIED,
            lines = lines
        )
    }

    private fun resolveAssetAccountCode(event: AccountingEvent): String {
        val symbol = event.tokenSymbol ?: "ETH"
        if (symbol == "ETH") {
            ensureAccountExists(ETH_ACCOUNT, "ETH 보유 자산", AccountCategory.ASSET, system = true)
            return ETH_ACCOUNT
        }

        val code = "$ERC20_PREFIX${symbol.uppercase()}"
        ensureAccountExists(code, "$symbol 보유 자산", AccountCategory.ASSET, system = true)
        return code
    }

    private fun ensureAccountExists(code: String, name: String, category: AccountCategory, system: Boolean) {
        val existing = accountRepository.findByCode(code)
        if (existing == null) {
            accountRepository.save(
                Account(
                    code = code,
                    name = name,
                    category = category,
                    system = system
                )
            )
        }
    }

    private fun line(
        accountCode: String,
        debit: BigDecimal = BigDecimal.ZERO,
        credit: BigDecimal = BigDecimal.ZERO,
        tokenSymbol: String? = null,
        tokenQuantity: BigDecimal? = null
    ): JournalLine {
        return JournalLine(
            accountCode = accountCode,
            debitAmount = debit.roundMoney(),
            creditAmount = credit.roundMoney(),
            tokenSymbol = tokenSymbol,
            tokenQuantity = tokenQuantity
        )
    }

    private fun gainLossLines(gainLoss: BigDecimal): List<JournalLine> {
        return when {
            gainLoss > BigDecimal.ZERO -> listOf(line(REALIZED_GAIN_ACCOUNT, credit = gainLoss))
            gainLoss < BigDecimal.ZERO -> listOf(line(REALIZED_LOSS_ACCOUNT, debit = gainLoss.abs()))
            else -> emptyList()
        }
    }

    private fun BigDecimal.roundMoney(): BigDecimal = setScale(8, RoundingMode.HALF_UP)
}
