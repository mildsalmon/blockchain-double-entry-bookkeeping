package com.example.ledger.application.dto

import com.example.ledger.domain.model.SyncStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

data class WalletCreateRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$")
    val address: String,
    val label: String? = null,
    @field:PositiveOrZero
    val startBlock: Long? = null
)

data class WalletResponse(
    val id: Long?,
    val address: String,
    val label: String?,
    val syncStatus: SyncStatus,
    val lastSyncedAt: Instant?,
    val lastSyncedBlock: Long?
)

data class WalletStatusResponse(
    val address: String,
    val syncStatus: SyncStatus,
    val lastSyncedAt: Instant?,
    val lastSyncedBlock: Long?
)
