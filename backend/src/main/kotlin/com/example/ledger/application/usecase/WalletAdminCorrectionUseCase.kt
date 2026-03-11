package com.example.ledger.application.usecase

import com.example.ledger.application.dto.WalletAdminCorrectionPreflightResponse
import com.example.ledger.application.dto.WalletAdminCorrectionImpactResponse
import com.example.ledger.application.dto.WalletOmittedSuspectedResponse
import com.example.ledger.application.dto.WalletResponse
import com.example.ledger.application.dto.WalletTokenPreviewResponse
import com.example.ledger.application.exception.ConflictException
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.WalletRepository
import com.example.ledger.domain.service.AuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

@Service
class WalletAdminCorrectionUseCase(
    private val walletRepository: WalletRepository,
    private val syncPipelineUseCase: SyncPipelineUseCase,
    private val walletCutoffInsightsService: WalletCutoffInsightsService,
    private val auditService: AuditService,
    private val adminCorrectionAvailability: AdminCorrectionAvailability,
    transactionManager: PlatformTransactionManager
) {
    private val logger = LoggerFactory.getLogger(WalletAdminCorrectionUseCase::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun preflight(
        address: String,
        tokenAddresses: List<String>,
        actor: String,
        approvalReference: String,
        reason: String
    ): WalletAdminCorrectionPreflightResponse {
        val wallet = loadEligibleWallet(address)
        return buildCorrectionPlan(
            wallet = wallet,
            tokenAddresses = tokenAddresses,
            actor = actor,
            approvalReference = approvalReference,
            reason = reason
        ).toResponse()
    }

    fun apply(
        address: String,
        tokenAddresses: List<String>,
        actor: String,
        approvalReference: String,
        reason: String,
        summaryHash: String
    ): WalletResponse {
        val normalizedSummaryHash = summaryHash.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("summaryHash is required")

        val updatedWallet = transactionTemplate.execute {
            val lockedWallet = walletRepository.findByAddressForUpdate(address)
                ?: throw IllegalArgumentException("Wallet not found: $address")
            ensureEligibleWallet(
                wallet = lockedWallet,
                hasApprovedCutoffBaseline = walletCutoffInsightsService.hasApprovedCutoffBaseline(address)
            )
            val plan = buildCorrectionPlan(
                wallet = lockedWallet,
                tokenAddresses = tokenAddresses,
                actor = actor,
                approvalReference = approvalReference,
                reason = reason
            )
            if (plan.summaryHash != normalizedSummaryHash) {
                throw IllegalArgumentException("summaryHash does not match current admin correction preview")
            }

            walletRepository.purgeLedgerData(address)

            val resetWallet = lockedWallet.copy(
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                syncStatus = SyncStatus.PENDING,
                snapshotBlock = null,
                deltaSyncedBlock = null,
                lastSyncedAt = null,
                lastSyncedBlock = plan.cutoffBlock,
                trackedTokens = plan.resultingTrackedTokens,
                updatedAt = Instant.now()
            )
            val savedWallet = walletRepository.save(resetWallet)

            auditService.log(
                entityType = CORRECTION_ENTITY_TYPE,
                entityId = address,
                action = CORRECTION_APPLY_ACTION,
                oldValue = mapOf(
                    "trackedTokens" to lockedWallet.trackedTokens,
                    "seededTokens" to plan.currentSeededTokens.map(::seededTokenPayload)
                ),
                newValue = mapOf(
                    "strategy" to CORRECTION_STRATEGY,
                    "requestedTokens" to plan.requestedTokens,
                    "addedTokens" to plan.addedTokens,
                    "trackedTokens" to plan.resultingTrackedTokens,
                    "cutoffBlock" to plan.cutoffBlock,
                    "appliedBy" to plan.actor,
                    "approvalReference" to plan.approvalReference,
                    "reason" to plan.reason,
                    "summaryHash" to plan.summaryHash,
                    "warnings" to plan.warnings,
                    "omittedCandidateMatches" to plan.omittedCandidateMatches.map(::omittedCandidatePayload),
                    "seededTokens" to plan.resultingSeededTokens.map(::seededTokenPayload)
                ),
                actor = plan.actor
            )
            logCorrectionSignOff(
                address = address,
                cutoffBlock = plan.cutoffBlock,
                trackedTokens = plan.resultingTrackedTokens,
                seededTokens = plan.resultingSeededTokens,
                actor = plan.actor,
                approvalReference = plan.approvalReference,
                reason = plan.reason
            )

            savedWallet
        } ?: throw IllegalStateException("Wallet correction transaction completed without a wallet result")

        try {
            syncPipelineUseCase.syncAsync(address)
        } catch (dispatchError: Exception) {
            logger.warn("Async correction replay dispatch failed, running inline fallback. walletAddress={}", address, dispatchError)
            syncPipelineUseCase.sync(address)
        }
        return updatedWallet.toResponse()
    }

    private fun loadEligibleWallet(address: String): Wallet {
        val wallet = walletRepository.findByAddress(address)
            ?: throw IllegalArgumentException("Wallet not found: $address")
        ensureEligibleWallet(
            wallet = wallet,
            hasApprovedCutoffBaseline = walletCutoffInsightsService.hasApprovedCutoffBaseline(address)
        )
        return wallet
    }

    private fun ensureEligibleWallet(wallet: Wallet, hasApprovedCutoffBaseline: Boolean) {
        val readiness = adminCorrectionAvailability.readinessFor(
            wallet,
            hasApprovedCutoffBaseline = hasApprovedCutoffBaseline
        )
        if (!readiness.enabled) {
            throw ConflictException(readiness.unavailableReason ?: "Admin correction is disabled on this server")
        }
        if (readiness.eligible) {
            return
        }

        val reason = readiness.ineligibleReason ?: "Wallet is not eligible for admin correction"
        if (wallet.syncMode != WalletSyncMode.BALANCE_FLOW_CUTOFF || wallet.cutoffBlock == null) {
            throw IllegalArgumentException(reason)
        }
        throw ConflictException(reason)
    }

    private fun buildCorrectionPlan(
        wallet: Wallet,
        tokenAddresses: List<String>,
        actor: String,
        approvalReference: String,
        reason: String
    ): CorrectionPlan {
        val normalizedActor = actor.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("actor is required")
        val normalizedApprovalReference = approvalReference.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("approvalReference is required")
        val normalizedReason = reason.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("reason is required")
        require(normalizedApprovalReference.length <= MAX_APPROVAL_REFERENCE_LENGTH) {
            "approvalReference exceeds $MAX_APPROVAL_REFERENCE_LENGTH characters"
        }
        require(normalizedReason.length <= MAX_REASON_LENGTH) {
            "reason exceeds $MAX_REASON_LENGTH characters"
        }
        val normalizedRequestedTokens = normalizeTrackedTokens(tokenAddresses)
        if (normalizedRequestedTokens.isEmpty()) {
            throw IllegalArgumentException("tokenAddresses is required")
        }
        require(normalizedRequestedTokens.size <= MAX_REQUESTED_TOKEN_COUNT) {
            "tokenAddresses exceeds $MAX_REQUESTED_TOKEN_COUNT items"
        }

        val currentTrackedTokens = wallet.trackedTokens
        val currentTrackedSet = currentTrackedTokens.toSet()
        val addedTokens = normalizedRequestedTokens.filterNot(currentTrackedSet::contains)
        if (addedTokens.isEmpty()) {
            throw ConflictException("All requested tokens are already tracked for this wallet")
        }

        val cutoffBlock = requireNotNull(wallet.cutoffBlock) { "cutoffBlock is required for correction plan" }
        val currentInsights = walletCutoffInsightsService.enrich(wallet)
        val omittedMatches = currentInsights.omittedSuspectedTokens.filter { candidate ->
            normalizedRequestedTokens.contains(candidate.tokenAddress.lowercase())
        }
        val resultingTrackedTokens = (currentTrackedTokens + addedTokens).distinct().sorted()
        val resultingPreflight = walletCutoffInsightsService.buildPreflight(wallet.address, cutoffBlock, resultingTrackedTokens)
        val impact = buildCorrectionImpact(wallet, cutoffBlock)

        val warnings = buildList {
            add("Admin correction will purge existing cutoff snapshots, delta transactions, journals, and lots before rebuilding them.")
            if (addedTokens.size != normalizedRequestedTokens.size) {
                add("Already tracked tokens are ignored; only newly added tokens trigger the restate.")
            }
            if (omittedMatches.isEmpty()) {
                add("None of the requested tokens match current omitted-suspected candidates. Additional operator review is required.")
            } else if (omittedMatches.size != addedTokens.size) {
                add("Some requested tokens do not match current omitted-suspected candidates and still require explicit admin judgment.")
            }
        }

        return CorrectionPlan(
            walletAddress = wallet.address,
            cutoffBlock = cutoffBlock,
            actor = normalizedActor,
            approvalReference = normalizedApprovalReference,
            reason = normalizedReason,
            requestedTokens = normalizedRequestedTokens,
            addedTokens = addedTokens,
            currentTrackedTokens = currentTrackedTokens,
            resultingTrackedTokens = resultingTrackedTokens,
            omittedCandidateMatches = omittedMatches,
            currentSeededTokens = currentInsights.seededTokens,
            resultingSeededTokens = resultingPreflight.seededTokens,
            impact = impact,
            summaryHash = buildCorrectionSummaryHash(
                walletAddress = wallet.address,
                cutoffBlock = cutoffBlock,
                resultingTrackedTokens = resultingTrackedTokens,
                requestedTokens = normalizedRequestedTokens,
                actor = normalizedActor,
                impact = impact,
                approvalReference = normalizedApprovalReference,
                reason = normalizedReason
            ),
            warnings = warnings
        )
    }

    private fun buildCorrectionImpact(
        wallet: Wallet,
        cutoffBlock: Long
    ): CorrectionImpact {
        val ledgerImpact = walletRepository.getLedgerDataImpact(wallet.address)
            ?: throw IllegalArgumentException("Wallet not found: ${wallet.address}")
        val replayToBlock = wallet.lastSyncedBlock ?: wallet.deltaSyncedBlock ?: wallet.snapshotBlock
        val replayBlockSpan = replayToBlock?.let { (it - cutoffBlock).coerceAtLeast(0) }
        return CorrectionImpact(
            snapshotCount = ledgerImpact.snapshotCount,
            rawTransactionCount = ledgerImpact.rawTransactionCount,
            accountingEventCount = ledgerImpact.accountingEventCount,
            journalEntryCount = ledgerImpact.journalEntryCount,
            costBasisLotCount = ledgerImpact.costBasisLotCount,
            replayFromBlock = cutoffBlock,
            replayToBlock = replayToBlock,
            replayBlockSpan = replayBlockSpan
        )
    }

    private fun logCorrectionSignOff(
        address: String,
        cutoffBlock: Long,
        trackedTokens: List<String>,
        seededTokens: List<WalletTokenPreviewResponse>,
        actor: String,
        approvalReference: String,
        reason: String
    ) {
        auditService.log(
            entityType = WalletCutoffInsightsService.SIGN_OFF_ENTITY_TYPE,
            entityId = address,
            action = WalletCutoffInsightsService.SIGN_OFF_ACTION,
            oldValue = null,
            newValue = mapOf(
                "cutoffBlock" to cutoffBlock,
                "trackedTokens" to trackedTokens,
                "seededTokens" to seededTokens.map(::seededTokenPayload),
                "seededTokenCount" to seededTokens.size,
                "summaryHash" to walletCutoffInsightsService.buildSignOffSummaryHash(address, cutoffBlock, trackedTokens),
                "source" to "ADMIN_CORRECTION",
                "approvalReference" to approvalReference,
                "reason" to reason
            ),
            actor = actor
        )
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

    private fun seededTokenPayload(token: WalletTokenPreviewResponse): Map<String, Any?> {
        return mapOf(
            "tokenAddress" to token.tokenAddress,
            "tokenSymbol" to token.tokenSymbol,
            "displayLabel" to token.displayLabel,
            "firstSeenBlock" to token.firstSeenBlock
        )
    }

    private fun omittedCandidatePayload(candidate: WalletOmittedSuspectedResponse): Map<String, Any?> {
        return mapOf(
            "tokenAddress" to candidate.tokenAddress,
            "tokenSymbol" to candidate.tokenSymbol,
            "displayLabel" to candidate.displayLabel,
            "firstSeenBlock" to candidate.firstSeenBlock,
            "reason" to candidate.reason
        )
    }

    private data class CorrectionPlan(
        val walletAddress: String,
        val cutoffBlock: Long,
        val actor: String,
        val approvalReference: String,
        val reason: String,
        val requestedTokens: List<String>,
        val addedTokens: List<String>,
        val currentTrackedTokens: List<String>,
        val resultingTrackedTokens: List<String>,
        val omittedCandidateMatches: List<WalletOmittedSuspectedResponse>,
        val currentSeededTokens: List<WalletTokenPreviewResponse>,
        val resultingSeededTokens: List<WalletTokenPreviewResponse>,
        val impact: CorrectionImpact,
        val summaryHash: String,
        val warnings: List<String>
    ) {
        fun toResponse(): WalletAdminCorrectionPreflightResponse {
            return WalletAdminCorrectionPreflightResponse(
                walletAddress = walletAddress,
                cutoffBlock = cutoffBlock,
                strategy = CORRECTION_STRATEGY,
                currentTrackedTokens = currentTrackedTokens,
                resultingTrackedTokens = resultingTrackedTokens,
                requestedTokens = requestedTokens,
                omittedCandidateMatches = omittedCandidateMatches,
                currentSeededTokens = currentSeededTokens,
                resultingSeededTokens = resultingSeededTokens,
                impact = impact.toResponse(),
                summaryHash = summaryHash,
                warnings = warnings
            )
        }
    }

    private data class CorrectionImpact(
        val snapshotCount: Int,
        val rawTransactionCount: Int,
        val accountingEventCount: Int,
        val journalEntryCount: Int,
        val costBasisLotCount: Int,
        val replayFromBlock: Long,
        val replayToBlock: Long?,
        val replayBlockSpan: Long?
    ) {
        fun toResponse(): WalletAdminCorrectionImpactResponse {
            return WalletAdminCorrectionImpactResponse(
                snapshotCount = snapshotCount,
                rawTransactionCount = rawTransactionCount,
                accountingEventCount = accountingEventCount,
                journalEntryCount = journalEntryCount,
                costBasisLotCount = costBasisLotCount,
                replayFromBlock = replayFromBlock,
                replayToBlock = replayToBlock,
                replayBlockSpan = replayBlockSpan
            )
        }
    }

    companion object {
        private const val CORRECTION_ENTITY_TYPE = "WALLET_CUTOFF_ADMIN_CORRECTION"
        private const val CORRECTION_APPLY_ACTION = "APPLY_SNAPSHOT_RESTATE"
        private const val CORRECTION_STRATEGY = "SNAPSHOT_RESTATE"
        private const val MAX_REQUESTED_TOKEN_COUNT = 20
        private const val MAX_APPROVAL_REFERENCE_LENGTH = 120
        private const val MAX_REASON_LENGTH = 500
        private val TOKEN_ADDRESS_REGEX = Regex("^0x[a-f0-9]{40}$")
    }

    private fun buildCorrectionSummaryHash(
        walletAddress: String,
        cutoffBlock: Long,
        resultingTrackedTokens: List<String>,
        requestedTokens: List<String>,
        actor: String,
        impact: CorrectionImpact,
        approvalReference: String,
        reason: String
    ): String {
        val seed = buildString {
            append(walletAddress.lowercase())
            append('|')
            append(cutoffBlock)
            append('|')
            append(resultingTrackedTokens.joinToString(","))
            append('|')
            append(requestedTokens.joinToString(","))
            append('|')
            append(actor)
            append('|')
            append(impact.snapshotCount)
            append('|')
            append(impact.rawTransactionCount)
            append('|')
            append(impact.accountingEventCount)
            append('|')
            append(impact.journalEntryCount)
            append('|')
            append(impact.costBasisLotCount)
            append('|')
            append(impact.replayFromBlock)
            append('|')
            append(impact.replayToBlock ?: "null")
            append('|')
            append(impact.replayBlockSpan ?: "null")
            append('|')
            append(approvalReference)
            append('|')
            append(reason)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
