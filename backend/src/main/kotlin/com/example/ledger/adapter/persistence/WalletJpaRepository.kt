package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.spring.SpringDataWalletRepository
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.port.WalletRepository
import org.springframework.stereotype.Repository

@Repository
class WalletJpaRepository(
    private val springDataWalletRepository: SpringDataWalletRepository
) : WalletRepository {
    override fun save(wallet: Wallet): Wallet {
        return springDataWalletRepository.save(wallet.toEntity()).toDomain()
    }

    override fun findByAddress(address: String): Wallet? {
        return springDataWalletRepository.findByAddress(address)?.toDomain()
    }

    override fun findAll(): List<Wallet> {
        return springDataWalletRepository.findAll().map { it.toDomain() }
    }

    override fun trySetSyncing(address: String): Boolean {
        return springDataWalletRepository.setStatusSyncingIfNotAlready(address) > 0
    }
}
