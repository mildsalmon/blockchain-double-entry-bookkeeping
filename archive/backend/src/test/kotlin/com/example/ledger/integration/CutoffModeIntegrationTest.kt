package com.example.ledger.integration

import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.model.WalletSyncPhase
import com.example.ledger.domain.port.BlockchainDataPort
import com.example.ledger.domain.port.PricePort
import com.example.ledger.domain.port.WalletRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigInteger

class CutoffModeIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @MockBean
    private lateinit var blockchainDataPort: BlockchainDataPort

    @MockBean
    private lateinit var pricePort: PricePort

    @Test
    fun `cutoff mode bypasses price enrichment path`() {
        val walletAddress = "0x4444444444444444444444444444444444444444"
        val cutoffBlock = 1_000L
        walletRepository.save(
            Wallet(
                address = walletAddress,
                syncMode = WalletSyncMode.BALANCE_FLOW_CUTOFF,
                syncPhase = WalletSyncPhase.SNAPSHOT_PENDING,
                cutoffBlock = cutoffBlock
            )
        )

        whenever(blockchainDataPort.getNativeBalanceAtBlock(eq(walletAddress), eq(cutoffBlock))).thenReturn(BigInteger.ZERO)
        whenever(blockchainDataPort.fetchTransactions(eq(walletAddress), eq(cutoffBlock + 1))).thenReturn(emptyList())

        syncPipelineUseCase.sync(walletAddress)

        verifyNoInteractions(pricePort)
    }
}
