package com.example.ledger.application.usecase

import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.port.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PendingWalletSyncRecovery(
    private val walletRepository: WalletRepository,
    private val syncPipelineUseCase: SyncPipelineUseCase,
    @Value("\${app.sync-recovery.pending-wallet-delay-ms:60000}")
    private val pendingWalletDelayMs: Long
) {
    private val logger = LoggerFactory.getLogger(PendingWalletSyncRecovery::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun recoverPendingWalletsOnStartup() {
        recoverPendingWallets("startup")
    }

    @Scheduled(
        initialDelayString = "\${app.sync-recovery.pending-wallet-delay-ms:60000}",
        fixedDelayString = "\${app.sync-recovery.pending-wallet-delay-ms:60000}"
    )
    fun recoverPendingWalletsOnSchedule() {
        recoverPendingWallets("schedule")
    }

    fun recoverPendingWallets(trigger: String = "manual") {
        val pendingWallets = walletRepository.findAll()
            .filter { it.syncStatus == SyncStatus.PENDING }
        if (pendingWallets.isEmpty()) {
            return
        }

        logger.info(
            "Recovering pending wallets. trigger={}, count={}, delayMs={}",
            trigger,
            pendingWallets.size,
            pendingWalletDelayMs
        )

        pendingWallets.forEach { wallet ->
            try {
                syncPipelineUseCase.syncAsync(wallet.address)
            } catch (dispatchError: Exception) {
                logger.warn(
                    "Async pending wallet recovery dispatch failed, running inline fallback. trigger={}, walletAddress={}",
                    trigger,
                    wallet.address,
                    dispatchError
                )
                syncPipelineUseCase.sync(wallet.address)
            }
        }
    }
}
