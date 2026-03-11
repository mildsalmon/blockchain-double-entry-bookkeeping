package com.example.ledger.integration

import com.example.ledger.application.usecase.IngestWalletUseCase
import com.example.ledger.application.usecase.SyncPipelineUseCase
import com.example.ledger.domain.port.WalletRepository
import com.example.ledger.domain.service.AuditService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WalletCutoffAtomicityIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var ingestWalletUseCase: IngestWalletUseCase

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @MockBean
    private lateinit var syncPipelineUseCase: SyncPipelineUseCase

    @MockBean
    private lateinit var auditService: AuditService

    @Test
    fun `cutoff wallet creation rolls back when signoff logging fails`() {
        val address = "0xefefefefefefefefefefefefefefefefefefefef"
        val cutoffBlock = 20_000_000L
        val summaryHash = ingestWalletUseCase.preflightCutoffWallet(address, cutoffBlock).summaryHash

        doThrow(IllegalStateException("audit storage unavailable"))
            .whenever(auditService)
            .log(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())
        clearInvocations(syncPipelineUseCase)

        assertFailsWith<IllegalStateException> {
            ingestWalletUseCase.registerWallet(
                address = address,
                reviewedBy = "ops-kim",
                preflightSummaryHash = summaryHash,
                mode = com.example.ledger.domain.model.WalletSyncMode.BALANCE_FLOW_CUTOFF,
                cutoffBlock = cutoffBlock
            )
        }

        assertNull(walletRepository.findByAddress(address))
        verifyNoInteractions(syncPipelineUseCase)
    }
}
