package com.example.ledger.application.usecase

import com.example.ledger.application.dto.WalletCutoffPreflightResponse
import com.example.ledger.application.dto.WalletTokenPreviewResponse
import com.example.ledger.application.dto.WalletResponse
import com.example.ledger.application.dto.WalletStatusResponse
import com.example.ledger.application.exception.ConflictException
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.WalletRepository
import com.example.ledger.domain.service.AuditService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class IngestWalletUseCase(
    private val walletRepository: WalletRepository,
    private val syncPipelineUseCase: SyncPipelineUseCase,
    private val auditService: AuditService,
    private val walletCutoffInsightsService: WalletCutoffInsightsService,
    private val adminCorrectionAvailability: AdminCorrectionAvailability,
    transactionManager: PlatformTransactionManager
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun registerWallet(
        address: String,
        label: String? = null,
        reviewedBy: String? = null,
        preflightSummaryHash: String? = null,
        mode: WalletSyncMode? = null,
        cutoffBlock: Long? = null,
        startBlock: Long? = null,
        trackedTokens: List<String>? = null
    ): WalletResponse {
        val existing = walletRepository.findByAddress(address)
        val resolvedMode = mode ?: existing?.syncMode ?: WalletSyncMode.FULL
        val normalizedReviewer = reviewedBy?.trim()?.takeIf { it.isNotBlank() }
        val normalizedPreflightSummaryHash = preflightSummaryHash?.trim()?.takeIf { it.isNotBlank() }
        val configOverrideRequested = hasConfigOverrideRequested(
            label = label,
            reviewedBy = normalizedReviewer,
            preflightSummaryHash = normalizedPreflightSummaryHash,
            mode = mode,
            cutoffBlock = cutoffBlock,
            startBlock = startBlock,
            trackedTokens = trackedTokens
        )
        if (existing != null) {
            ensureNoConfigOverrideRequested(configOverrideRequested)
            syncPipelineUseCase.syncAsync(address)
            return existing.toResponse()
        }

        val normalizedTrackedTokens = normalizeTrackedTokens(trackedTokens.orEmpty())
        if (resolvedMode == WalletSyncMode.FULL && normalizedTrackedTokens.isNotEmpty()) {
            throw IllegalArgumentException("trackedTokens requires BALANCE_FLOW_CUTOFF mode")
        }
        if (resolvedMode == WalletSyncMode.BALANCE_FLOW_CUTOFF && normalizedReviewer == null) {
            throw IllegalArgumentException("reviewedBy is required in BALANCE_FLOW_CUTOFF mode")
        }

        val resolvedCutoffBlock = resolveCutoffBlockForMode(resolvedMode, cutoffBlock, startBlock)
        if (resolvedMode == WalletSyncMode.BALANCE_FLOW_CUTOFF && normalizedPreflightSummaryHash == null) {
            throw IllegalArgumentException("preflightSummaryHash is required in BALANCE_FLOW_CUTOFF mode")
        }
        val cutoffPreflight = if (resolvedMode == WalletSyncMode.BALANCE_FLOW_CUTOFF) {
            walletCutoffInsightsService.buildPreflight(
                address = address,
                cutoffBlock = requireNotNull(resolvedCutoffBlock) { "cutoffBlock is required in BALANCE_FLOW_CUTOFF mode" },
                trackedTokens = normalizedTrackedTokens
            ).also { preflight ->
                if (preflight.summaryHash != normalizedPreflightSummaryHash) {
                    throw IllegalArgumentException("preflightSummaryHash does not match current cutoff preview")
                }
            }
        } else {
            null
        }
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
            transactionTemplate.execute {
                val savedWallet = walletRepository.save(
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

                if (resolvedMode == WalletSyncMode.BALANCE_FLOW_CUTOFF) {
                    logCutoffSeedSignOff(
                        address = address,
                        cutoffBlock = requireNotNull(resolvedCutoffBlock) { "cutoffBlock is required for cutoff sign-off" },
                        trackedTokens = normalizedTrackedTokens,
                        seededTokens = requireNotNull(cutoffPreflight) { "cutoff preflight is required for sign-off" }.seededTokens,
                        reviewedBy = requireNotNull(normalizedReviewer) { "reviewedBy is required for cutoff sign-off" }
                    )
                }

                savedWallet
            } ?: throw IllegalStateException("Wallet registration transaction completed without a wallet result")
        } catch (e: DataIntegrityViolationException) {
            val persistedWallet = walletRepository.findByAddress(address)
                ?: throw IllegalStateException("Wallet registration race detected but wallet was not found", e)
            ensureNoConfigOverrideRequested(
                configOverrideRequested,
                "Wallet was registered concurrently. Retry without configuration changes, or delete and re-register to change configuration"
            )
            syncPipelineUseCase.syncAsync(address)
            return persistedWallet.toResponse()
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

        return wallet.toStatusResponse()
    }

    fun retrySync(address: String): WalletResponse {
        val wallet = walletRepository.findByAddress(address)
            ?: throw IllegalArgumentException("Wallet not found: $address")
        syncPipelineUseCase.syncAsync(address)
        return wallet.toResponse()
    }

    fun preflightCutoffWallet(
        address: String,
        cutoffBlock: Long,
        trackedTokens: List<String>? = null
    ): WalletCutoffPreflightResponse {
        val normalizedTrackedTokens = normalizeTrackedTokens(trackedTokens.orEmpty())
        return walletCutoffInsightsService.buildPreflight(address, cutoffBlock, normalizedTrackedTokens)
    }

    fun deleteWallet(address: String) {
        val deleted = walletRepository.deleteByAddress(address)
        if (!deleted) {
            throw IllegalArgumentException("Wallet not found: $address")
        }
    }

    private fun Wallet.toResponse(): WalletResponse {
        val cutoffInsights = walletCutoffInsightsService.enrich(this)
        val adminCorrectionReadiness = adminCorrectionAvailability.readinessFor(
            this,
            hasApprovedCutoffBaseline = cutoffInsights.latestCutoffSignOff != null
        )
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
            seededTokens = cutoffInsights.seededTokens,
            discoveredTokens = cutoffInsights.discoveredTokens,
            omittedSuspectedTokens = cutoffInsights.omittedSuspectedTokens,
            latestCutoffSignOff = cutoffInsights.latestCutoffSignOff,
            adminCorrectionEnabled = adminCorrectionReadiness.enabled,
            adminCorrectionUnavailableReason = adminCorrectionReadiness.unavailableReason,
            adminCorrectionEligible = adminCorrectionReadiness.eligible,
            adminCorrectionIneligibleReason = adminCorrectionReadiness.ineligibleReason,
            lastSyncedAt = lastSyncedAt,
            lastSyncedBlock = lastSyncedBlock
        )
    }

    private fun Wallet.toStatusResponse(): WalletStatusResponse {
        val cutoffInsights = walletCutoffInsightsService.enrich(this)
        val adminCorrectionReadiness = adminCorrectionAvailability.readinessFor(
            this,
            hasApprovedCutoffBaseline = cutoffInsights.latestCutoffSignOff != null
        )
        return WalletStatusResponse(
            address = address,
            mode = syncMode,
            syncPhase = syncPhase,
            syncStatus = syncStatus,
            cutoffBlock = cutoffBlock,
            snapshotBlock = snapshotBlock,
            deltaSyncedBlock = deltaSyncedBlock,
            trackedTokens = trackedTokens,
            seededTokens = cutoffInsights.seededTokens,
            discoveredTokens = cutoffInsights.discoveredTokens,
            omittedSuspectedTokens = cutoffInsights.omittedSuspectedTokens,
            latestCutoffSignOff = cutoffInsights.latestCutoffSignOff,
            adminCorrectionEnabled = adminCorrectionReadiness.enabled,
            adminCorrectionUnavailableReason = adminCorrectionReadiness.unavailableReason,
            adminCorrectionEligible = adminCorrectionReadiness.eligible,
            adminCorrectionIneligibleReason = adminCorrectionReadiness.ineligibleReason,
            lastSyncedAt = lastSyncedAt,
            lastSyncedBlock = lastSyncedBlock
        )
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

    private fun hasConfigOverrideRequested(
        label: String?,
        reviewedBy: String?,
        preflightSummaryHash: String?,
        mode: WalletSyncMode?,
        cutoffBlock: Long?,
        startBlock: Long?,
        trackedTokens: List<String>?
    ): Boolean {
        return label != null ||
            reviewedBy != null ||
            preflightSummaryHash != null ||
            mode != null ||
            cutoffBlock != null ||
            startBlock != null ||
            !trackedTokens.isNullOrEmpty()
    }

    private fun ensureNoConfigOverrideRequested(
        configOverrideRequested: Boolean,
        message: String = "Wallet already exists. Use retry to resync, or delete and re-register to change configuration"
    ) {
        if (!configOverrideRequested) {
            return
        }

        throw ConflictException(message)
    }

    private fun logCutoffSeedSignOff(
        address: String,
        cutoffBlock: Long,
        trackedTokens: List<String>,
        seededTokens: List<WalletTokenPreviewResponse>,
        reviewedBy: String
    ) {
        auditService.log(
            entityType = WalletCutoffInsightsService.SIGN_OFF_ENTITY_TYPE,
            entityId = address,
            action = WalletCutoffInsightsService.SIGN_OFF_ACTION,
            oldValue = null,
            newValue = mapOf(
                "cutoffBlock" to cutoffBlock,
                "trackedTokens" to trackedTokens,
                "seededTokens" to seededTokens.map { token ->
                    mapOf(
                        "tokenAddress" to token.tokenAddress,
                        "tokenSymbol" to token.tokenSymbol,
                        "displayLabel" to token.displayLabel
                    )
                },
                "seededTokenCount" to seededTokens.size,
                "summaryHash" to walletCutoffInsightsService.buildSignOffSummaryHash(address, cutoffBlock, trackedTokens),
                "source" to "INITIAL_REGISTRATION"
            ),
            actor = reviewedBy
        )
    }

    companion object {
        private val TOKEN_ADDRESS_REGEX = Regex("^0x[a-f0-9]{40}$")
    }
}
