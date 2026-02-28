package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.entity.WalletTrackedTokenEntity
import com.example.ledger.adapter.persistence.spring.SpringDataWalletRepository
import com.example.ledger.adapter.persistence.spring.SpringDataWalletTrackedTokenRepository
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.port.WalletRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class WalletJpaRepository(
    private val springDataWalletRepository: SpringDataWalletRepository,
    private val springDataWalletTrackedTokenRepository: SpringDataWalletTrackedTokenRepository
) : WalletRepository {
    @Transactional
    override fun save(wallet: Wallet): Wallet {
        val saved = springDataWalletRepository.save(wallet.toEntity())
        val walletId = requireNotNull(saved.id) { "wallet id should exist after save" }

        springDataWalletTrackedTokenRepository.deleteByWalletId(walletId)
        springDataWalletTrackedTokenRepository.flush()
        if (wallet.trackedTokens.isNotEmpty()) {
            val tokenEntities = wallet.trackedTokens.map { token ->
                WalletTrackedTokenEntity(
                    walletId = walletId,
                    tokenAddress = token
                )
            }
            springDataWalletTrackedTokenRepository.saveAll(tokenEntities)
        }

        return saved.toDomain().copy(
            trackedTokens = springDataWalletTrackedTokenRepository
                .findByWalletIdOrderByTokenAddressAsc(walletId)
                .map { it.tokenAddress }
        )
    }

    override fun findByAddress(address: String): Wallet? {
        val wallet = springDataWalletRepository.findByAddress(address) ?: return null
        val walletId = requireNotNull(wallet.id) { "wallet id should exist when reading by address" }
        val trackedTokens = springDataWalletTrackedTokenRepository
            .findByWalletIdOrderByTokenAddressAsc(walletId)
            .map { it.tokenAddress }
        return wallet.toDomain().copy(trackedTokens = trackedTokens)
    }

    override fun findAll(): List<Wallet> {
        return springDataWalletRepository.findAll().map { wallet ->
            val walletId = requireNotNull(wallet.id) { "wallet id should exist when listing wallets" }
            val trackedTokens = springDataWalletTrackedTokenRepository
                .findByWalletIdOrderByTokenAddressAsc(walletId)
                .map { it.tokenAddress }
            wallet.toDomain().copy(trackedTokens = trackedTokens)
        }
    }
}
