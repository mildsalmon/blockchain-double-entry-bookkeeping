package com.example.ledger.domain.port

import com.example.ledger.domain.model.Wallet

interface WalletRepository {
    fun save(wallet: Wallet): Wallet
    fun findByAddress(address: String): Wallet?
    fun findAll(): List<Wallet>
}
