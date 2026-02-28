package com.example.ledger.application.usecase

import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletBalanceSnapshot
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.WalletBalanceSnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val NATIVE_ETH_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000"

@Service
class CutoffSnapshotService(
    private val blockchainDataPort: BlockchainDataPort,
    private val walletBalanceSnapshotRepository: WalletBalanceSnapshotRepository
) {
    @Transactional
    fun collect(wallet: Wallet): List<WalletBalanceSnapshot> {
        val walletId = requireNotNull(wallet.id) { "Wallet id is required to create cutoff snapshot" }
        val cutoffBlock = wallet.cutoffBlock
            ?: throw IllegalArgumentException("cutoffBlock is required for cutoff snapshot")

        val snapshots = mutableListOf<WalletBalanceSnapshot>()

        snapshots += WalletBalanceSnapshot(
            walletId = walletId,
            tokenAddress = NATIVE_ETH_TOKEN_ADDRESS,
            tokenSymbol = "ETH",
            balanceRaw = blockchainDataPort.getNativeBalanceAtBlock(wallet.address, cutoffBlock),
            cutoffBlock = cutoffBlock
        )

        wallet.trackedTokens.forEach { tokenAddress ->
            snapshots += WalletBalanceSnapshot(
                walletId = walletId,
                tokenAddress = tokenAddress,
                tokenSymbol = "ERC20",
                balanceRaw = blockchainDataPort.getTokenBalanceAtBlock(wallet.address, tokenAddress, cutoffBlock),
                cutoffBlock = cutoffBlock
            )
        }

        walletBalanceSnapshotRepository.deleteByWalletId(walletId)
        return walletBalanceSnapshotRepository.saveAll(snapshots)
    }
}
