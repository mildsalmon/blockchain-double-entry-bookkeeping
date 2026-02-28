package com.example.ledger.application.usecase

import com.example.ledger.application.dto.WalletResponse
import com.example.ledger.application.dto.WalletStatusResponse
import com.example.ledger.application.exception.ConflictException
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.WalletRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class IngestWalletUseCase(
    private val walletRepository: WalletRepository,
    private val syncPipelineUseCase: SyncPipelineUseCase
) {

    fun registerWallet(
        address: String,
        label: String? = null,
        mode: WalletSyncMode? = null,
        cutoffBlock: Long? = null,
        startBlock: Long? = null,
        trackedTokens: List<String>? = null
    ): WalletResponse {
        val normalizedTrackedTokens = normalizeTrackedTokens(trackedTokens.orEmpty())
        val resolvedMode = resolveMode(mode)
        if (resolvedMode == WalletSyncMode.FULL && normalizedTrackedTokens.isNotEmpty()) {
            throw IllegalArgumentException("trackedTokens requires BALANCE_FLOW_CUTOFF mode")
        }

        val existing = walletRepository.findByAddress(address)
        if (existing != null) {
            val requestedCutoff = when (resolvedMode) {
                WalletSyncMode.FULL -> null
                WalletSyncMode.BALANCE_FLOW_CUTOFF -> cutoffBlock ?: startBlock ?: existing.cutoffBlock
            }
            ensureMutableConfig(existing, resolvedMode, requestedCutoff, normalizedTrackedTokens)
            syncPipelineUseCase.syncAsync(address)
            return existing.toResponse()
        }

        val resolvedCutoffBlock = resolveCutoffBlockForMode(resolvedMode, cutoffBlock, startBlock)
        val syncPhase = if (resolvedMode == WalletSyncMode.BALANCE_FLOW_CUTOFF) {
            WalletSyncPhase.SNAPSHOT_PENDING
        } else {
            WalletSyncPhase.NONE
        }
        val initialLastSyncedBlock = when (resolvedMode) {
            WalletSyncMode.FULL -> startBlock
            WalletSyncMode.BALANCE_FLOW_CUTOFF -> resolvedCutoffBlock
        }

        val wallet = try {
            walletRepository.save(
                Wallet(
                    address = address,
                    label = label,
                    syncMode = resolvedMode,
                    syncPhase = syncPhase,
                    syncStatus = SyncStatus.PENDING,
                    cutoffBlock = resolvedCutoffBlock,
                    trackedTokens = normalizedTrackedTokens,
                    lastSyncedAt = null,
                    lastSyncedBlock = initialLastSyncedBlock,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )
        } catch (e: DataIntegrityViolationException) {
            walletRepository.findByAddress(address)
                ?: throw IllegalStateException("Wallet registration race detected but wallet was not found", e)
        }

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
            mode = wallet.syncMode,
            syncPhase = wallet.syncPhase,
            syncStatus = wallet.syncStatus,
            cutoffBlock = wallet.cutoffBlock,
            snapshotBlock = wallet.snapshotBlock,
            deltaSyncedBlock = wallet.deltaSyncedBlock,
            trackedTokens = wallet.trackedTokens,
            lastSyncedAt = wallet.lastSyncedAt,
            lastSyncedBlock = wallet.lastSyncedBlock
        )
    }

    private fun Wallet.toResponse(): WalletResponse {
        return WalletResponse(
            id = id,
            address = address,
            label = label,
            mode = syncMode,
            syncPhase = syncPhase,
            syncStatus = syncStatus,
            cutoffBlock = cutoffBlock,
            snapshotBlock = snapshotBlock,
            deltaSyncedBlock = deltaSyncedBlock,
            trackedTokens = trackedTokens,
            lastSyncedAt = lastSyncedAt,
            lastSyncedBlock = lastSyncedBlock
        )
    }

    private fun resolveMode(mode: WalletSyncMode?): WalletSyncMode {
        return mode ?: WalletSyncMode.FULL
    }

    private fun resolveCutoffBlockForMode(mode: WalletSyncMode, cutoffBlock: Long?, startBlock: Long?): Long? {
        return when (mode) {
            WalletSyncMode.FULL -> {
                if (cutoffBlock != null) {
                    throw IllegalArgumentException("cutoffBlock requires BALANCE_FLOW_CUTOFF mode")
                }
                null
            }

            WalletSyncMode.BALANCE_FLOW_CUTOFF -> {
                val resolved = cutoffBlock ?: startBlock
                resolved ?: throw IllegalArgumentException("cutoffBlock (or legacy startBlock) is required in BALANCE_FLOW_CUTOFF mode")
            }
        }
    }

    private fun normalizeTrackedTokens(tokens: List<String>): List<String> {
        val normalized = tokens
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.lowercase() }

        normalized.forEach { token ->
            if (!TOKEN_ADDRESS_REGEX.matches(token)) {
                throw IllegalArgumentException("Invalid tracked token address: $token")
            }
        }
        return normalized.distinct().sorted()
    }

    private fun ensureMutableConfig(
        existing: Wallet,
        requestedMode: WalletSyncMode,
        requestedCutoffBlock: Long?,
        requestedTrackedTokens: List<String>
    ) {
        if (existing.syncMode == WalletSyncMode.BALANCE_FLOW_CUTOFF && requestedMode == WalletSyncMode.FULL) {
            return
        }

        if (existing.syncMode != requestedMode) {
            throw ConflictException("Wallet mode cannot be changed after registration")
        }

        if (existing.syncMode == WalletSyncMode.BALANCE_FLOW_CUTOFF && existing.snapshotBlock != null) {
            val cutoffChanged = requestedCutoffBlock != null && requestedCutoffBlock != existing.cutoffBlock
            val tokensChanged = requestedTrackedTokens.isNotEmpty() && requestedTrackedTokens != existing.trackedTokens
            if (cutoffChanged || tokensChanged) {
                throw ConflictException("cutoffBlock and trackedTokens are immutable after snapshot")
            }
        }
    }

    companion object {
        private val TOKEN_ADDRESS_REGEX = Regex("^0x[a-f0-9]{40}$")
    }
}
