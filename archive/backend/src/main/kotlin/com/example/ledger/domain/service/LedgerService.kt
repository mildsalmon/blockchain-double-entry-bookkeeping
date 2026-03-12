package com.example.ledger.domain.service

import com.example.ledger.config.CannotSerializeTransactionException
import com.example.ledger.config.SerializableTx
import com.example.ledger.domain.model.AccountCategory
import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.model.WalletBalanceSnapshot
import com.example.ledger.domain.port.AccountRepository
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.JournalRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.CannotAcquireLockException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant

@Service
class LedgerService(
    private val journalRepository: JournalRepository,
    private val accountRepository: AccountRepository,
    private val blockchainDataPort: BlockchainDataPort,
    private val fifoService: FifoService,
    private val gainLossService: GainLossService,
    private val auditService: AuditService,
    private val tokenMetadataService: TokenMetadataService
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
        private const val EXTERNAL_ASSET_ACCOUNT = "자산:외부"
        private const val NATIVE_ETH_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000"
        private val WEI_PER_ETH = BigDecimal.TEN.pow(18)
    }

    private val logger = LoggerFactory.getLogger(LedgerService::class.java)

    @Retryable(
        retryFor = [CannotSerializeTransactionException::class, CannotAcquireLockException::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 50, multiplier = 2.0, maxDelay = 1000)
    )
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

    @Recover
    fun recoverAfterSerializationFailure(
        ex: CannotSerializeTransactionException,
        walletAddress: String,
        rawTransactionId: Long,
        events: List<AccountingEvent>,
        entryDate: Instant
    ): List<JournalEntry> {
        logger.error(
            "Ledger entry generation retries exhausted due to serialization failures. walletAddress={}, rawTransactionId={}, eventCount={}, entryDate={}",
            walletAddress, rawTransactionId, events.size, entryDate, ex
        )
        throw ex
    }

    @Recover
    fun recoverAfterLockFailure(
        ex: CannotAcquireLockException,
        walletAddress: String,
        rawTransactionId: Long,
        events: List<AccountingEvent>,
        entryDate: Instant
    ): List<JournalEntry> {
        logger.error(
            "Ledger entry generation retries exhausted due to lock failures. walletAddress={}, rawTransactionId={}, eventCount={}, entryDate={}",
            walletAddress, rawTransactionId, events.size, entryDate, ex
        )
        throw ex
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

    fun generateBalanceFlowOpeningEntries(
        rawTransactionId: Long,
        snapshots: List<WalletBalanceSnapshot>,
        entryDate: Instant
    ): List<JournalEntry> {
        if (snapshots.isEmpty()) return emptyList()
        ensureExternalAccountExists()

        return snapshots
            .filter { it.balanceRaw > BigInteger.ZERO }
            .map { snapshot ->
                val quantity = snapshotBalanceToQuantity(snapshot)
                if (quantity <= BigDecimal.ZERO) {
                    return@map null
                }

                val tokenSymbol = balanceFlowSymbol(snapshot.tokenSymbol, snapshot.tokenAddress)
                val assetAccountCode = resolveBalanceFlowAssetAccountCode(tokenSymbol, snapshot.tokenAddress)
                val tokenAddress = normalizedLedgerTokenAddress(snapshot.tokenAddress)
                val tokenChain = ledgerTokenChain(tokenAddress)
                JournalEntry(
                    rawTransactionId = rawTransactionId,
                    entryDate = entryDate,
                    description = "Cutoff opening balance $tokenSymbol",
                    status = JournalStatus.AUTO_CLASSIFIED,
                    lines = listOf(
                        line(assetAccountCode, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity),
                        line(EXTERNAL_ASSET_ACCOUNT, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity.negate())
                    )
                )
            }
            .filterNotNull()
            .map { journalRepository.save(it) }
    }

    fun generateBalanceFlowDeltaEntries(
        rawTransactionId: Long,
        events: List<AccountingEvent>,
        entryDate: Instant
    ): List<JournalEntry> {
        if (events.isEmpty()) return emptyList()
        ensureExternalAccountExists()

        return events.mapNotNull { event ->
            val entry = createBalanceFlowDeltaEntry(rawTransactionId, event, entryDate) ?: return@mapNotNull null
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

    private fun createBalanceFlowDeltaEntry(
        rawTransactionId: Long,
        event: AccountingEvent,
        entryDate: Instant
    ): JournalEntry? {
        val tokenSymbol = balanceFlowSymbol(event.tokenSymbol, event.tokenAddress)
        val tokenAccountCode = resolveBalanceFlowAssetAccountCode(tokenSymbol, event.tokenAddress)
        val tokenAddress = normalizedLedgerTokenAddress(event.tokenAddress)
        val tokenChain = ledgerTokenChain(tokenAddress)
        val quantity = event.amountDecimal.abs()

        return when (event.eventType) {
            EventType.INCOMING ->
                JournalEntry(
                    accountingEventId = event.id,
                    rawTransactionId = rawTransactionId,
                    entryDate = entryDate,
                    description = "Incoming $tokenSymbol",
                    status = JournalStatus.AUTO_CLASSIFIED,
                    lines = listOf(
                        line(tokenAccountCode, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity),
                        line(EXTERNAL_ASSET_ACCOUNT, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity.negate())
                    )
                )

            EventType.OUTGOING ->
                JournalEntry(
                    accountingEventId = event.id,
                    rawTransactionId = rawTransactionId,
                    entryDate = entryDate,
                    description = "Outgoing $tokenSymbol",
                    status = JournalStatus.AUTO_CLASSIFIED,
                    lines = listOf(
                        line(EXTERNAL_ASSET_ACCOUNT, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity),
                        line(tokenAccountCode, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity.negate())
                    )
                )

            EventType.FEE ->
                JournalEntry(
                    accountingEventId = event.id,
                    rawTransactionId = rawTransactionId,
                    entryDate = entryDate,
                    description = "Gas fee",
                    status = JournalStatus.AUTO_CLASSIFIED,
                    lines = listOf(
                        line(GAS_FEE_ACCOUNT, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity),
                        line(tokenAccountCode, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity.negate())
                    )
                )

            EventType.SWAP -> {
                val tokenOutSymbol = balanceFlowSymbol(
                    event.metadata["tokenOutSymbol"]?.toString(),
                    event.metadata["tokenOutAddress"]?.toString()
                )
                val tokenOutAddress = event.metadata["tokenOutAddress"]?.toString()
                val normalizedTokenOutAddress = normalizedLedgerTokenAddress(tokenOutAddress)
                val tokenOutChain = ledgerTokenChain(normalizedTokenOutAddress)
                val tokenOutAmount = event.metadata["amountOut"]?.toString()?.toBigDecimalOrNull()?.abs() ?: BigDecimal.ZERO
                val tokenOutAccount = resolveBalanceFlowAssetAccountCode(tokenOutSymbol, tokenOutAddress)

                JournalEntry(
                    accountingEventId = event.id,
                    rawTransactionId = rawTransactionId,
                    entryDate = entryDate,
                    description = "Swap $tokenSymbol -> $tokenOutSymbol",
                    status = JournalStatus.AUTO_CLASSIFIED,
                    lines = listOf(
                        line(tokenOutAccount, tokenSymbol = tokenOutSymbol, chain = tokenOutChain, tokenAddress = normalizedTokenOutAddress, tokenQuantity = tokenOutAmount),
                        line(tokenAccountCode, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity.negate())
                    )
                )
            }

            EventType.MANUAL_CLASSIFIED ->
                JournalEntry(
                    accountingEventId = event.id,
                    rawTransactionId = rawTransactionId,
                    entryDate = entryDate,
                    description = "Incoming $tokenSymbol",
                    status = JournalStatus.AUTO_CLASSIFIED,
                    lines = listOf(
                        line(tokenAccountCode, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity),
                        line(AIRDROP_ACCOUNT, tokenSymbol = tokenSymbol, chain = tokenChain, tokenAddress = tokenAddress, tokenQuantity = quantity.negate())
                    )
                )

            EventType.UNCLASSIFIED -> null
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
        val tokenSymbol = event.tokenSymbol ?: "ETH"
        val tokenAddress = normalizedLedgerTokenAddress(event.tokenAddress)
        val tokenChain = ledgerTokenChain(tokenAddress)
        val assetAccountCode = resolveAssetAccountCode(event.copy(tokenSymbol = tokenSymbol))
        fifoService.addLot(
            walletAddress = walletAddress,
            tokenSymbol = tokenSymbol,
            chain = tokenChain,
            tokenAddress = tokenAddress,
            quantity = event.amountDecimal,
            unitCostKrw = event.priceKrw ?: BigDecimal.ZERO,
            rawTransactionId = rawTransactionId,
            acquisitionDate = entryDate
        )

        return JournalEntry(
            accountingEventId = event.id,
            rawTransactionId = rawTransactionId,
            entryDate = entryDate,
            description = "Incoming $tokenSymbol",
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
        val tokenSymbol = event.tokenSymbol ?: "ETH"
        val tokenAddress = normalizedLedgerTokenAddress(event.tokenAddress)
        val tokenChain = ledgerTokenChain(tokenAddress)
        val assetAccountCode = resolveAssetAccountCode(event.copy(tokenSymbol = tokenSymbol))
        val fifo = fifoService.consume(walletAddress, tokenSymbol, event.amountDecimal, tokenChain, tokenAddress)
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
            description = "Outgoing $tokenSymbol",
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
        // TODO(multichain-fees): ETH is the native gas asset for the current Ethereum-only scope.
        // When non-Ethereum chains are added, derive the fee asset from the transaction chain's native token.
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
        val tokenInAddress = normalizedLedgerTokenAddress(event.tokenAddress)
        val tokenOutAddress = normalizedLedgerTokenAddress(event.metadata["tokenOutAddress"]?.toString())
        val tokenInChain = ledgerTokenChain(tokenInAddress)
        val tokenOutChain = ledgerTokenChain(tokenOutAddress)
        val tokenOutAmount = event.metadata["amountOut"]?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        val tokenInAccount = resolveAssetAccountCode(event)
        val tokenOutAccount = resolveAssetAccountCode(
            event.copy(
                tokenSymbol = tokenOutSymbol,
                tokenAddress = tokenOutAddress
            )
        )

        val fifo = fifoService.consume(walletAddress, tokenInSymbol, event.amountDecimal, tokenInChain, tokenInAddress)
        val gainLoss = gainLossService.calculate(proceedsKrw, fifo.totalCostKrw)

        if (tokenOutAmount > BigDecimal.ZERO) {
            val unitCost = if (tokenOutAmount == BigDecimal.ZERO) BigDecimal.ZERO else proceedsKrw.divide(tokenOutAmount, MathContext.DECIMAL128)
            fifoService.addLot(
                walletAddress = walletAddress,
                tokenSymbol = tokenOutSymbol,
                chain = tokenOutChain,
                tokenAddress = tokenOutAddress,
                quantity = tokenOutAmount,
                unitCostKrw = unitCost,
                rawTransactionId = rawTransactionId,
                acquisitionDate = entryDate
            )
        }

        val lines = mutableListOf(
            line(tokenOutAccount, debit = proceedsKrw, tokenSymbol = tokenOutSymbol, chain = tokenOutChain, tokenAddress = tokenOutAddress, tokenQuantity = tokenOutAmount),
            line(tokenInAccount, credit = fifo.totalCostKrw, tokenSymbol = tokenInSymbol, chain = tokenInChain, tokenAddress = tokenInAddress, tokenQuantity = event.amountDecimal.negate())
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
        val tokenAddress = normalizedLedgerTokenAddress(event.tokenAddress)
        if (tokenAddress == null && symbol == "ETH") {
            ensureAccountExists(ETH_ACCOUNT, "ETH 보유 자산", AccountCategory.ASSET, system = true)
            return ETH_ACCOUNT
        }

        val code = erc20AccountCode(symbol, tokenAddress)
        ensureAccountExists(code, assetAccountName(symbol, tokenAddress), AccountCategory.ASSET, system = true)
        return code
    }

    private fun resolveBalanceFlowAssetAccountCode(tokenSymbol: String, tokenAddress: String?): String {
        val normalizedTokenAddress = normalizedLedgerTokenAddress(tokenAddress)
        if (normalizedTokenAddress == null && tokenSymbol == "ETH") {
            ensureAccountExists(ETH_ACCOUNT, "ETH 보유 자산", AccountCategory.ASSET, system = true)
            return ETH_ACCOUNT
        }

        val code = erc20AccountCode(tokenSymbol, normalizedTokenAddress)
        ensureAccountExists(code, assetAccountName(tokenSymbol, normalizedTokenAddress), AccountCategory.ASSET, system = true)
        return code
    }

    private fun balanceFlowSymbol(tokenSymbol: String?, tokenAddress: String?): String {
        if (tokenAddress.equals(NATIVE_ETH_TOKEN_ADDRESS, ignoreCase = true)) {
            return "ETH"
        }
        return tokenMetadataService.normalizeSymbol(tokenSymbol) ?: TokenMetadataService.ERR_SYMBOL
    }

    private fun snapshotBalanceToQuantity(snapshot: WalletBalanceSnapshot): BigDecimal {
        return if (snapshot.tokenAddress.equals(NATIVE_ETH_TOKEN_ADDRESS, ignoreCase = true)) {
            snapshot.balanceRaw.toBigDecimal().divide(WEI_PER_ETH, 18, RoundingMode.DOWN)
        } else {
            val decimals = blockchainDataPort.getTokenDecimals(snapshot.tokenAddress, snapshot.cutoffBlock)
                ?: return snapshot.balanceRaw.toBigDecimal()
            snapshot.balanceRaw.toBigDecimal()
                .movePointLeft(decimals)
        }
    }

    private fun ensureAccountExists(code: String, name: String, category: AccountCategory, system: Boolean) {
        accountRepository.insertIfAbsent(code, name, category, system)
        accountRepository.findByCode(code)
            ?: throw IllegalStateException("Account upsert failed for code=$code")
    }

    private fun ensureExternalAccountExists() {
        ensureAccountExists(EXTERNAL_ASSET_ACCOUNT, "외부 상대 계정", AccountCategory.ASSET, system = true)
    }

    private fun line(
        accountCode: String,
        debit: BigDecimal = BigDecimal.ZERO,
        credit: BigDecimal = BigDecimal.ZERO,
        tokenSymbol: String? = null,
        chain: String? = null,
        tokenAddress: String? = null,
        tokenQuantity: BigDecimal? = null
    ): JournalLine {
        return JournalLine(
            accountCode = accountCode,
            debitAmount = debit.roundMoney(),
            creditAmount = credit.roundMoney(),
            tokenSymbol = tokenSymbol,
            chain = chain,
            tokenAddress = tokenAddress,
            tokenQuantity = tokenQuantity
        )
    }

    private fun normalizedLedgerTokenAddress(tokenAddress: String?): String? {
        return tokenMetadataService.normalizeContractAddress(tokenAddress)
    }

    private fun ledgerTokenChain(tokenAddress: String?): String? {
        return if (tokenAddress == null) null else TokenMetadataService.ETHEREUM_CHAIN
    }

    private fun erc20AccountCode(tokenSymbol: String, tokenAddress: String?): String {
        return if (tokenAddress.isNullOrBlank()) {
            "$ERC20_PREFIX${tokenSymbol.uppercase()}"
        } else {
            "$ERC20_PREFIX${tokenSymbol.uppercase()}@${tokenAddress.lowercase()}"
        }
    }

    private fun assetAccountName(tokenSymbol: String, tokenAddress: String?): String {
        return if (tokenAddress.isNullOrBlank()) {
            "$tokenSymbol 보유 자산"
        } else {
            "$tokenSymbol 보유 자산 (${tokenAddress.takeLast(6)})"
        }
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
