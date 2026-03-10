package com.example.ledger.application.dto

import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
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
    val summaryHash: String
)

data class WalletCutoffPreflightResponse(
    val address: String,
    val cutoffBlock: Long,
    val includesNativeEth: Boolean,
    val seededTokens: List<WalletTokenPreviewResponse>,
    val summaryHash: String,
    val warning: String
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
    val lastSyncedAt: Instant?,
    val lastSyncedBlock: Long?
)
