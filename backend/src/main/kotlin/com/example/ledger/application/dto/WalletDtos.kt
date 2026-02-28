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
    val mode: WalletSyncMode? = null,
    @field:PositiveOrZero
    val cutoffBlock: Long? = null,
    @field:PositiveOrZero
    val startBlock: Long? = null,
    val trackedTokens: List<String>? = null
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
    val lastSyncedAt: Instant?,
    val lastSyncedBlock: Long?
)
