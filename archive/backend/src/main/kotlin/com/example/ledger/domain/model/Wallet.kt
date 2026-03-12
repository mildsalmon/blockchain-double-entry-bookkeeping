package com.example.ledger.domain.model

import java.time.Instant

data class Wallet(
    val id: Long? = null,
    val address: String,
    val label: String? = null,
    val syncMode: WalletSyncMode = WalletSyncMode.FULL,
    val syncPhase: WalletSyncPhase = WalletSyncPhase.NONE,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val cutoffBlock: Long? = null,
    val snapshotBlock: Long? = null,
    val deltaSyncedBlock: Long? = null,
    val lastSyncedAt: Instant? = null,
    val lastSyncedBlock: Long? = null,
    val trackedTokens: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
