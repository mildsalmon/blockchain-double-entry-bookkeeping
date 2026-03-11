package com.example.ledger.application.dto

import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

data class WalletCreateRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$")
    val address: String,
    val label: String? = null,
    val reviewedBy: String? = null,
    val preflightSummaryHash: String? = null,
    val mode: WalletSyncMode? = null,
    @field:PositiveOrZero
    val cutoffBlock: Long? = null,
    @field:PositiveOrZero
    val startBlock: Long? = null,
    val trackedTokens: List<String>? = null
)

data class WalletCutoffPreflightRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$")
    val address: String,
    @field:PositiveOrZero
    val cutoffBlock: Long,
    val trackedTokens: List<String>? = null
)

data class WalletAdminCorrectionPreflightRequest(
    @field:NotEmpty
    @field:Size(max = 20)
    val tokenAddresses: List<@Pattern(regexp = "^0x[a-fA-F0-9]{40}$") String>,
    @field:NotBlank
    @field:Size(max = 120)
    val approvalReference: String,
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String
)

data class WalletAdminCorrectionApplyRequest(
    @field:NotEmpty
    @field:Size(max = 20)
    val tokenAddresses: List<@Pattern(regexp = "^0x[a-fA-F0-9]{40}$") String>,
    @field:NotBlank
    @field:Size(max = 120)
    val approvalReference: String,
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[a-f0-9]{64}$")
    val summaryHash: String
)

data class WalletTokenPreviewResponse(
    val tokenAddress: String?,
    val tokenSymbol: String,
    val displayLabel: String,
    val firstSeenBlock: Long? = null,
    val firstSeenAt: Instant? = null
)

data class WalletOmittedSuspectedResponse(
    val tokenAddress: String,
    val tokenSymbol: String,
    val displayLabel: String,
    val firstSeenBlock: Long,
    val firstSeenAt: Instant?,
    val reason: String
)

data class WalletCutoffSignOffResponse(
    val reviewedBy: String,
    val reviewedAt: Instant,
    val cutoffBlock: Long,
    val seededTokenCount: Int,
    val summaryHash: String,
    val source: String? = null,
    val approvalReference: String? = null,
    val reason: String? = null
)

data class WalletCutoffPreflightResponse(
    val address: String,
    val cutoffBlock: Long,
    val includesNativeEth: Boolean,
    val seededTokens: List<WalletTokenPreviewResponse>,
    val summaryHash: String,
    val warning: String
)

data class WalletAdminCorrectionPreflightResponse(
    val walletAddress: String,
    val cutoffBlock: Long,
    val strategy: String,
    val currentTrackedTokens: List<String>,
    val resultingTrackedTokens: List<String>,
    val requestedTokens: List<String>,
    val omittedCandidateMatches: List<WalletOmittedSuspectedResponse>,
    val currentSeededTokens: List<WalletTokenPreviewResponse>,
    val resultingSeededTokens: List<WalletTokenPreviewResponse>,
    val impact: WalletAdminCorrectionImpactResponse,
    val summaryHash: String,
    val warnings: List<String>
)

data class WalletAdminCorrectionImpactResponse(
    val snapshotCount: Int,
    val rawTransactionCount: Int,
    val accountingEventCount: Int,
    val journalEntryCount: Int,
    val costBasisLotCount: Int,
    val replayFromBlock: Long,
    val replayToBlock: Long?,
    val replayBlockSpan: Long?
)

data class WalletResponse(
    val id: Long?,
    val address: String,
    val label: String?,
    val mode: WalletSyncMode,
    val syncPhase: WalletSyncPhase,
    val syncStatus: SyncStatus,
    val cutoffBlock: Long?,
    val snapshotBlock: Long?,
    val deltaSyncedBlock: Long?,
    val trackedTokens: List<String>,
    val seededTokens: List<WalletTokenPreviewResponse> = emptyList(),
    val discoveredTokens: List<WalletTokenPreviewResponse> = emptyList(),
    val omittedSuspectedTokens: List<WalletOmittedSuspectedResponse> = emptyList(),
    val latestCutoffSignOff: WalletCutoffSignOffResponse? = null,
    val adminCorrectionEnabled: Boolean = false,
    val adminCorrectionUnavailableReason: String? = null,
    val adminCorrectionEligible: Boolean = false,
    val adminCorrectionIneligibleReason: String? = null,
    val lastSyncedAt: Instant?,
    val lastSyncedBlock: Long?
)

data class WalletStatusResponse(
    val address: String,
    val mode: WalletSyncMode,
    val syncPhase: WalletSyncPhase,
    val syncStatus: SyncStatus,
    val cutoffBlock: Long?,
    val snapshotBlock: Long?,
    val deltaSyncedBlock: Long?,
    val trackedTokens: List<String>,
    val seededTokens: List<WalletTokenPreviewResponse> = emptyList(),
    val discoveredTokens: List<WalletTokenPreviewResponse> = emptyList(),
    val omittedSuspectedTokens: List<WalletOmittedSuspectedResponse> = emptyList(),
    val latestCutoffSignOff: WalletCutoffSignOffResponse? = null,
    val adminCorrectionEnabled: Boolean = false,
    val adminCorrectionUnavailableReason: String? = null,
    val adminCorrectionEligible: Boolean = false,
    val adminCorrectionIneligibleReason: String? = null,
    val lastSyncedAt: Instant?,
    val lastSyncedBlock: Long?
)
