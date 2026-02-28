package com.example.ledger.domain.port

import com.example.ledger.domain.model.WalletBalanceSnapshot

interface WalletBalanceSnapshotRepository {
    fun saveAll(snapshots: List<WalletBalanceSnapshot>): List<WalletBalanceSnapshot>
    fun findByWalletId(walletId: Long): List<WalletBalanceSnapshot>
    fun deleteByWalletId(walletId: Long)
}
