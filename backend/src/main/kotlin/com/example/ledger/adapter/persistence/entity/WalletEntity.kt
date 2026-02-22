package com.example.ledger.adapter.persistence.entity

import com.example.ledger.domain.model.SyncStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "wallets")
data class WalletEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "address", nullable = false, unique = true)
    val address: String,
    @Column(name = "label")
    val label: String? = null,
    @Column(name = "sync_status", nullable = false)
    val syncStatus: String = SyncStatus.PENDING.name,
    @Column(name = "last_synced_at")
    val lastSyncedAt: Instant? = null,
    @Column(name = "last_synced_block")
    val lastSyncedBlock: Long? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
