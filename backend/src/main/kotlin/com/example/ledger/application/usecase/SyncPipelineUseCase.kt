package com.example.ledger.application.usecase

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.port.AccountingEventRepository
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.RawTransactionRepository
import com.example.ledger.domain.port.TransactionDecoderPort
import com.example.ledger.domain.port.WalletRepository
import com.example.ledger.domain.service.ClassificationService
import com.example.ledger.domain.service.LedgerService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset

@Service
class SyncPipelineUseCase(
    private val walletRepository: WalletRepository,
    private val blockchainDataPort: BlockchainDataPort,
    private val rawTransactionRepository: RawTransactionRepository,
    private val transactionDecoder: TransactionDecoderPort,
    private val classificationService: ClassificationService,
    private val accountingEventRepository: AccountingEventRepository,
    private val pricePort: PricePort,
    private val ledgerService: LedgerService
) {
    private val logger = LoggerFactory.getLogger(SyncPipelineUseCase::class.java)

    @Async
    fun syncAsync(walletAddress: String) {
        sync(walletAddress)
    }

    fun sync(walletAddress: String) {
        val wallet = walletRepository.findByAddress(walletAddress)
            ?: throw IllegalArgumentException("Wallet not found: $walletAddress")

        if (!walletRepository.trySetSyncing(walletAddress)) {
            logger.info("Wallet is already syncing. walletAddress={}", walletAddress)
            return
        }

        try {
            val rawTransactions = blockchainDataPort.fetchTransactions(walletAddress, wallet.lastSyncedBlock)
            val savedRawTransactions = rawTransactionRepository.saveAll(rawTransactions)
                .sortedWith(
                    compareBy<RawTransaction> { it.blockNumber }
                        .thenBy { it.txIndex ?: Int.MAX_VALUE }
                        .thenBy { it.txHash }
                )

            var maxBlock = wallet.lastSyncedBlock ?: 0L

            savedRawTransactions.forEach { rawTx ->
                if (rawTx.blockNumber > maxBlock) {
                    maxBlock = rawTx.blockNumber
                }

                val decoded = transactionDecoder.decode(rawTx)
                val classified = classificationService.classify(decoded)
                val enriched = classified.map { enrichPrice(it, rawTx.blockTimestamp) }
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
            walletRepository.save(wallet.copy(syncStatus = SyncStatus.FAILED, updatedAt = Instant.now()))
            throw ex
        }
    }

    private fun enrichPrice(event: AccountingEvent, blockTimestamp: Instant): AccountingEvent {
        val symbol = event.tokenSymbol ?: "ETH"
        val priceInfo = pricePort.getPrice(event.tokenAddress, symbol, blockTimestamp.atOffset(ZoneOffset.UTC).toLocalDate())
        return event.copy(
            priceKrw = priceInfo.priceKrw,
            priceSource = priceInfo.source.takeIf { it != PriceSource.UNKNOWN } ?: PriceSource.UNKNOWN
        )
    }
}
