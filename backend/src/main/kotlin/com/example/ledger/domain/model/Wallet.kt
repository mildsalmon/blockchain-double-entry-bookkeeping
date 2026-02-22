package com.example.ledger.domain.model

import java.time.Instant

data class Wallet(
    val id: Long? = null,
    val address: String,
    val label: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val lastSyncedAt: Instant? = null,
    val lastSyncedBlock: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
