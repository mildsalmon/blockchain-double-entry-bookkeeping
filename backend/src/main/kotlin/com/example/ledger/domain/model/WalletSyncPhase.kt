package com.example.ledger.domain.model

enum class WalletSyncPhase {
    NONE,
    SNAPSHOT_PENDING,
    SNAPSHOTTING,
    SNAPSHOT_COMPLETED,
    DELTA_SYNCING,
    DELTA_COMPLETED,
    FAILED
}
