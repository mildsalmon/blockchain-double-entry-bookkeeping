package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.WalletBalanceSnapshotRepository
import com.example.ledger.domain.port.WalletRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

private const val NATIVE_ETH_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000"

class CutoffSnapshotIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var walletBalanceSnapshotRepository: WalletBalanceSnapshotRepository

    @Autowired
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @Test
    fun `cutoff sync creates snapshot rows for ETH and tracked tokens`() {
        val address = "0x1111111111111111111111111111111111111111"
        val tokenA = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val tokenB = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val cutoffBlock = 100L
        val wallet = walletRepository.save(
            Wallet(
                address = address,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                cutoffBlock = cutoffBlock,
                trackedTokens = listOf(tokenA, tokenB)
            )
        )

        whenever(blockchainDataPort.getNativeBalanceAtBlock(eq(address), eq(cutoffBlock))).thenReturn(BigInteger("123"))
        whenever(blockchainDataPort.getTokenBalanceAtBlock(eq(address), eq(tokenA), eq(cutoffBlock))).thenReturn(BigInteger("456"))
        whenever(blockchainDataPort.getTokenBalanceAtBlock(eq(address), eq(tokenB), eq(cutoffBlock))).thenReturn(BigInteger("789"))
        whenever(blockchainDataPort.fetchTransactions(eq(address), eq(cutoffBlock + 1))).thenReturn(emptyList())

        syncPipelineUseCase.sync(address)

        val savedWallet = walletRepository.findByAddress(address)
        assertNotNull(savedWallet)
        assertEquals(SyncStatus.COMPLETED, savedWallet.syncStatus)
        assertEquals(WalletSyncPhase.DELTA_COMPLETED, savedWallet.syncPhase)
        assertEquals(cutoffBlock, savedWallet.snapshotBlock)
        assertEquals(cutoffBlock, savedWallet.deltaSyncedBlock)

        val snapshots = walletBalanceSnapshotRepository.findByWalletId(requireNotNull(wallet.id))
        assertEquals(3, snapshots.size)
        assertEquals(
            setOf(NATIVE_ETH_TOKEN_ADDRESS, tokenA, tokenB),
            snapshots.map { it.tokenAddress }.toSet()
        )
    }

    @Test
    fun `snapshot failure sets cutoff wallet to failed phase`() {
        val address = "0x2222222222222222222222222222222222222222"
        val cutoffBlock = 200L
        walletRepository.save(
            Wallet(
                address = address,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                cutoffBlock = cutoffBlock
            )
        )

        whenever(blockchainDataPort.getNativeBalanceAtBlock(eq(address), eq(cutoffBlock)))
            .thenThrow(RuntimeException("rpc timeout"))

        assertFailsWith<RuntimeException> {
            syncPipelineUseCase.sync(address)
        }

        val savedWallet = walletRepository.findByAddress(address)
        assertNotNull(savedWallet)
        assertEquals(SyncStatus.FAILED, savedWallet.syncStatus)
        assertEquals(WalletSyncPhase.FAILED, savedWallet.syncPhase)
    }
}
