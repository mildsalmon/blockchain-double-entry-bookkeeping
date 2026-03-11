package com.example.ledger.domain.port

import com.example.ledger.domain.model.Wallet

data class WalletLedgerDataImpact(
    val snapshotCount: Int,
    val rawTransactionCount: Int,
    val accountingEventCount: Int,
    val journalEntryCount: Int,
    val costBasisLotCount: Int
)

interface WalletRepository {
    fun save(wallet: Wallet): Wallet
    fun findByAddress(address: String): Wallet?
    fun findByAddressForUpdate(address: String): Wallet?
    fun findAll(): List<Wallet>
    fun trySetSyncing(address: String): Boolean
    fun getLedgerDataImpact(address: String): WalletLedgerDataImpact?
    fun purgeLedgerData(address: String): Boolean
    fun deleteByAddress(address: String): Boolean
}
