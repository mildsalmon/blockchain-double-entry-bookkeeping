package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.spring.SpringDataWalletBalanceSnapshotRepository
import com.example.ledger.domain.model.WalletBalanceSnapshot
import com.example.ledger.domain.port.WalletBalanceSnapshotRepository
import org.springframework.stereotype.Repository

@Repository
class WalletBalanceSnapshotJpaRepository(
    private val springDataWalletBalanceSnapshotRepository: SpringDataWalletBalanceSnapshotRepository
) : WalletBalanceSnapshotRepository {
    override fun saveAll(snapshots: List<WalletBalanceSnapshot>): List<WalletBalanceSnapshot> {
        if (snapshots.isEmpty()) return emptyList()
        return springDataWalletBalanceSnapshotRepository
            .saveAll(snapshots.map { it.toEntity() })
            .map { it.toDomain() }
    }

    override fun findByWalletId(walletId: Long): List<WalletBalanceSnapshot> {
        return springDataWalletBalanceSnapshotRepository.findByWalletIdOrderByIdAsc(walletId).map { it.toDomain() }
    }

    override fun deleteByWalletId(walletId: Long) {
        springDataWalletBalanceSnapshotRepository.deleteByWalletId(walletId)
    }
}
