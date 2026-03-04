package com.example.ledger.application.usecase

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.AccountingEventRepository
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.RawTransactionRepository
import com.example.ledger.domain.port.TransactionDecoderPort
import com.example.ledger.domain.port.WalletBalanceSnapshotRepository
import com.example.ledger.domain.port.WalletRepository
import com.example.ledger.domain.service.ClassificationService
import com.example.ledger.domain.service.LedgerService
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset

private const val NATIVE_ETH_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000"
private const val TOKEN_SYMBOL_MAX_LENGTH = 20

@Service
class SyncPipelineUseCase(
    private val walletRepository: WalletRepository,
    private val blockchainDataPort: BlockchainDataPort,
    private val rawTransactionRepository: RawTransactionRepository,
    private val transactionDecoder: TransactionDecoderPort,
    private val classificationService: ClassificationService,
    private val accountingEventRepository: AccountingEventRepository,
    private val walletBalanceSnapshotRepository: WalletBalanceSnapshotRepository,
    private val journalRepository: JournalRepository,
    private val pricePort: PricePort,
    private val ledgerService: LedgerService,
    private val cutoffSnapshotService: CutoffSnapshotService
) {
    private val logger = LoggerFactory.getLogger(SyncPipelineUseCase::class.java)

    @Async
    fun syncAsync(walletAddress: String) {
        sync(walletAddress)
    }

    fun sync(walletAddress: String) {
        val loadedWallet = walletRepository.findByAddress(walletAddress)
            ?: throw IllegalArgumentException("Wallet not found: $walletAddress")

        if (!walletRepository.trySetSyncing(walletAddress)) {
            logger.info("Wallet is already syncing. walletAddress={}", walletAddress)
            return
        }

        val wallet = walletRepository.findByAddress(walletAddress) ?: loadedWallet.copy(
            syncStatus = SyncStatus.SYNCING,
            updatedAt = Instant.now()
        )

        if (wallet.syncMode == WalletSyncMode.BALANCE_FLOW_CUTOFF) {
            syncCutoffMode(wallet)
            return
        }

        syncFullMode(wallet)
    }

    private fun syncFullMode(wallet: Wallet) {
        val walletAddress = wallet.address

        try {
            val rawTransactions = blockchainDataPort.fetchTransactions(walletAddress, wallet.lastSyncedBlock)
            val savedRawTransactions = persistAndSort(rawTransactions)
            var maxBlock = wallet.lastSyncedBlock ?: 0L

            savedRawTransactions.forEach { rawTx ->
                if (rawTx.blockNumber > maxBlock) {
                    maxBlock = rawTx.blockNumber
                }

                val decoded = transactionDecoder.decode(rawTx)
                val classified = classificationService.classify(decoded)
                val normalized = normalizeTokenEvents(classified, rawTx.blockNumber)
                val enriched = normalized.map { enrichPrice(it, rawTx.blockTimestamp) }
                val savedEvents = accountingEventRepository.saveAll(enriched)

                ledgerService.generateEntries(
                    walletAddress = walletAddress,
                    rawTransactionId = rawTx.id ?: return@forEach,
                    events = savedEvents,
                    entryDate = rawTx.blockTimestamp
                )
            }

            walletRepository.save(
                wallet.copy(
                    syncStatus = SyncStatus.COMPLETED,
                    lastSyncedAt = Instant.now(),
                    lastSyncedBlock = maxBlock,
                    updatedAt = Instant.now()
                )
            )
        } catch (ex: Exception) {
            val latestWallet = walletRepository.findByAddress(walletAddress) ?: wallet
            walletRepository.save(latestWallet.copy(syncStatus = SyncStatus.FAILED, updatedAt = Instant.now()))
            throw ex
        }
    }

    private fun syncCutoffMode(wallet: Wallet) {
        val walletAddress = wallet.address
        val initialPhase = if (wallet.snapshotBlock == null) {
            WalletSyncPhase.SNAPSHOTTING
        } else {
            WalletSyncPhase.DELTA_SYNCING
        }
        walletRepository.save(
            wallet.copy(
                syncStatus = SyncStatus.SYNCING,
                syncPhase = initialPhase,
                updatedAt = Instant.now()
            )
        )

        try {
            var currentWallet = walletRepository.findByAddress(walletAddress)
                ?: throw IllegalArgumentException("Wallet not found: $walletAddress")

            if (currentWallet.snapshotBlock == null) {
                val snapshots = cutoffSnapshotService.collect(currentWallet)
                ensureCutoffOpeningEntries(currentWallet, snapshots)
                val cutoffBlock = currentWallet.cutoffBlock
                    ?: throw IllegalArgumentException("cutoffBlock is required for BALANCE_FLOW_CUTOFF mode")
                currentWallet = walletRepository.save(
                    currentWallet.copy(
                        syncPhase = WalletSyncPhase.SNAPSHOT_COMPLETED,
                        snapshotBlock = cutoffBlock,
                        deltaSyncedBlock = cutoffBlock,
                        lastSyncedBlock = cutoffBlock,
                        updatedAt = Instant.now()
                    )
                )
            } else {
                ensureCutoffOpeningEntries(currentWallet)
            }

            val baseBlock = currentWallet.deltaSyncedBlock
                ?: currentWallet.cutoffBlock
                ?: throw IllegalArgumentException("cutoffBlock is required for BALANCE_FLOW_CUTOFF mode")
            val deltaFromBlock = baseBlock + 1
            val rawTransactions = blockchainDataPort.fetchTransactions(walletAddress, deltaFromBlock)
            val savedRawTransactions = persistAndSort(rawTransactions)

            var maxBlock = baseBlock
            savedRawTransactions.forEach { rawTx ->
                if (rawTx.blockNumber > maxBlock) {
                    maxBlock = rawTx.blockNumber
                }

                val decoded = transactionDecoder.decode(rawTx)
                val classified = classificationService.classify(decoded)
                val normalized = normalizeTokenEvents(classified, rawTx.blockNumber)
                val savedEvents = accountingEventRepository.saveAll(normalized)
                ledgerService.generateBalanceFlowDeltaEntries(
                    rawTransactionId = rawTx.id ?: return@forEach,
                    events = savedEvents,
                    entryDate = rawTx.blockTimestamp
                )
            }

            walletRepository.save(
                currentWallet.copy(
                    syncStatus = SyncStatus.COMPLETED,
                    syncPhase = WalletSyncPhase.DELTA_COMPLETED,
                    lastSyncedAt = Instant.now(),
                    deltaSyncedBlock = maxBlock,
                    lastSyncedBlock = maxBlock,
                    updatedAt = Instant.now()
                )
            )
        } catch (ex: Exception) {
            val latestWallet = walletRepository.findByAddress(walletAddress) ?: wallet
            walletRepository.save(
                latestWallet.copy(
                    syncStatus = SyncStatus.FAILED,
                    syncPhase = WalletSyncPhase.FAILED,
                    updatedAt = Instant.now()
                )
            )
            throw ex
        }
    }

    private fun persistAndSort(rawTransactions: List<RawTransaction>): List<RawTransaction> {
        return rawTransactionRepository.saveAll(rawTransactions)
            .sortedWith(
                compareBy<RawTransaction> { it.blockNumber }
                    .thenBy { it.txIndex ?: Int.MAX_VALUE }
                    .thenBy { it.txHash }
            )
    }

    private fun enrichPrice(event: AccountingEvent, blockTimestamp: Instant): AccountingEvent {
        val symbol = event.tokenSymbol ?: "ETH"
        val priceInfo = pricePort.getPrice(event.tokenAddress, symbol, blockTimestamp.atOffset(ZoneOffset.UTC).toLocalDate())
        return event.copy(
            priceKrw = priceInfo.priceKrw,
            priceSource = priceInfo.source.takeIf { it != PriceSource.UNKNOWN } ?: PriceSource.UNKNOWN
        )
    }

    private fun normalizeTokenEvents(events: List<AccountingEvent>, blockNumber: Long): List<AccountingEvent> {
        return events.map { event -> normalizeTokenEvent(event, blockNumber) }
    }

    private fun normalizeTokenEvent(event: AccountingEvent, blockNumber: Long): AccountingEvent {
        val normalizedMetadata = if (event.eventType == EventType.SWAP) {
            normalizeSwapMetadata(event.metadata, blockNumber)
        } else {
            event.metadata
        }

        val tokenAddress = event.tokenAddress
        if (tokenAddress.isNullOrBlank()) {
            val normalizedSymbol = if (event.tokenSymbol?.equals("ETH", ignoreCase = true) == true) {
                "ETH"
            } else {
                normalizeTokenSymbol(event.tokenSymbol) ?: event.tokenSymbol
            }
            return event.copy(tokenSymbol = normalizedSymbol, metadata = normalizedMetadata)
        }
        if (tokenAddress.equals(NATIVE_ETH_TOKEN_ADDRESS, ignoreCase = true)) {
            return event.copy(tokenSymbol = "ETH", metadata = normalizedMetadata)
        }

        val normalizedSymbol = normalizeTokenSymbol(blockchainDataPort.getTokenSymbol(tokenAddress, blockNumber))
            ?: normalizeTokenSymbol(event.tokenSymbol)
            ?: event.tokenSymbol

        val decimals = blockchainDataPort.getTokenDecimals(tokenAddress, blockNumber)
        val normalizedAmountDecimal = normalizeAmountDecimal(event, decimals)

        return event.copy(
            tokenSymbol = normalizedSymbol,
            amountDecimal = normalizedAmountDecimal,
            metadata = normalizedMetadata
        )
    }

    private fun normalizeAmountDecimal(event: AccountingEvent, decimals: Int?): BigDecimal {
        if (decimals == null || decimals < 0) return event.amountDecimal

        val rawAsDecimal = event.amountRaw.toBigDecimal()
        if (event.amountDecimal.compareTo(rawAsDecimal) != 0) {
            return event.amountDecimal
        }

        return rawAsDecimal.movePointLeft(decimals)
    }

    private fun normalizeSwapMetadata(metadata: Map<String, Any?>, blockNumber: Long): Map<String, Any?> {
        val tokenOutAddress = metadata["tokenOutAddress"]?.toString()?.takeIf { it.isNotBlank() } ?: return metadata

        val normalized = metadata.toMutableMap()
        val normalizedOutSymbol = normalizeTokenSymbol(blockchainDataPort.getTokenSymbol(tokenOutAddress, blockNumber))
            ?: normalizeTokenSymbol(metadata["tokenOutSymbol"]?.toString())
        if (normalizedOutSymbol != null) {
            normalized["tokenOutSymbol"] = normalizedOutSymbol
        }

        val decimals = blockchainDataPort.getTokenDecimals(tokenOutAddress, blockNumber)
        val normalizedOutAmount = normalizeMetadataAmount(metadata["amountOut"], decimals)
        if (normalizedOutAmount != null) {
            normalized["amountOut"] = normalizedOutAmount
        }

        return normalized
    }

    private fun normalizeMetadataAmount(value: Any?, decimals: Int?): BigDecimal? {
        val parsed = value.toBigDecimalOrNull() ?: return null
        if (decimals == null || decimals < 0) return parsed
        if (!isIntegerValue(parsed)) return parsed
        return parsed.movePointLeft(decimals)
    }

    private fun normalizeTokenSymbol(symbol: String?): String? {
        return symbol
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(TOKEN_SYMBOL_MAX_LENGTH)
            ?.uppercase()
    }

    private fun Any?.toBigDecimalOrNull(): BigDecimal? {
        return when (this) {
            null -> null
            is BigDecimal -> this
            is BigInteger -> this.toBigDecimal()
            is Int -> BigDecimal.valueOf(toLong())
            is Long -> BigDecimal.valueOf(this)
            is Float -> toBigDecimal()
            is Double -> toBigDecimal()
            is String -> toBigDecimalOrNull()
            else -> toString().toBigDecimalOrNull()
        }
    }

    private fun isIntegerValue(value: BigDecimal): Boolean {
        return value.stripTrailingZeros().scale() <= 0
    }

    private fun ensureCutoffOpeningEntries(wallet: Wallet, snapshotsOverride: List<com.example.ledger.domain.model.WalletBalanceSnapshot>? = null) {
        val walletId = wallet.id ?: return
        val cutoffBlock = wallet.snapshotBlock ?: wallet.cutoffBlock ?: return
        val snapshots = snapshotsOverride ?: walletBalanceSnapshotRepository.findByWalletId(walletId)
        if (snapshots.isEmpty()) {
            return
        }

        val snapshotRawTransaction = getOrCreateCutoffSnapshotRawTransaction(wallet.address, cutoffBlock)
        val snapshotRawTransactionId = snapshotRawTransaction.id
            ?: throw IllegalStateException("Synthetic cutoff snapshot raw transaction must have id")

        if (journalRepository.existsByRawTransactionId(snapshotRawTransactionId)) {
            return
        }

        ledgerService.generateBalanceFlowOpeningEntries(
            rawTransactionId = snapshotRawTransactionId,
            snapshots = snapshots,
            entryDate = Instant.now()
        )
    }

    private fun getOrCreateCutoffSnapshotRawTransaction(walletAddress: String, cutoffBlock: Long): RawTransaction {
        val syntheticTxHash = syntheticCutoffSnapshotTxHash(walletAddress, cutoffBlock)
        val rawData = JsonNodeFactory.instance.objectNode()
            .put("synthetic", "cutoff_snapshot")
            .put("walletAddress", walletAddress)
            .put("cutoffBlock", cutoffBlock)

        val syntheticRawTransaction = RawTransaction(
            walletAddress = walletAddress,
            txHash = syntheticTxHash,
            blockNumber = cutoffBlock,
            txIndex = 0,
            blockTimestamp = Instant.now(),
            rawData = rawData,
            txStatus = 1
        )

        val saved = rawTransactionRepository.saveAll(listOf(syntheticRawTransaction)).firstOrNull()
        if (saved != null) {
            return saved
        }

        return rawTransactionRepository.findByWalletAddress(walletAddress)
            .firstOrNull { it.txHash.equals(syntheticTxHash, ignoreCase = true) }
            ?: throw IllegalStateException("Synthetic cutoff snapshot raw transaction not found. txHash=$syntheticTxHash")
    }

    private fun syntheticCutoffSnapshotTxHash(walletAddress: String, cutoffBlock: Long): String {
        val seed = "cutoff_snapshot|${walletAddress.lowercase()}|$cutoffBlock"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(StandardCharsets.UTF_8))
        val hex = digest.joinToString("") { byte -> "%02x".format(byte) }
        return "0x$hex"
    }
}
