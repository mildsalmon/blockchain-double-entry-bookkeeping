package com.example.ledger.application.usecase

import com.example.ledger.application.dto.WalletResponse
import com.example.ledger.application.dto.WalletStatusResponse
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.port.WalletRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class IngestWalletUseCase(
    private val walletRepository: WalletRepository,
    private val syncPipelineUseCase: SyncPipelineUseCase
) {

    fun registerWallet(address: String, label: String? = null, startBlock: Long? = null): WalletResponse {
        val existing = walletRepository.findByAddress(address)
        if (existing != null) {
            syncPipelineUseCase.syncAsync(address)
            return existing.toResponse()
        }

        val wallet = walletRepository.save(
            Wallet(
                address = address,
                label = label,
                syncStatus = SyncStatus.PENDING,
                lastSyncedAt = null,
                lastSyncedBlock = startBlock,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        syncPipelineUseCase.syncAsync(address)
        return wallet.toResponse()
    }

    fun listWallets(): List<WalletResponse> {
        return walletRepository.findAll().map { it.toResponse() }
    }

    fun getStatus(address: String): WalletStatusResponse {
        val wallet = walletRepository.findByAddress(address)
            ?: throw IllegalArgumentException("Wallet not found: $address")

        return WalletStatusResponse(
            address = wallet.address,
            syncStatus = wallet.syncStatus,
            lastSyncedAt = wallet.lastSyncedAt,
            lastSyncedBlock = wallet.lastSyncedBlock
        )
    }

    private fun Wallet.toResponse(): WalletResponse {
        return WalletResponse(
            id = id,
            address = address,
            label = label,
            syncStatus = syncStatus,
            lastSyncedAt = lastSyncedAt,
            lastSyncedBlock = lastSyncedBlock
        )
    }
}
