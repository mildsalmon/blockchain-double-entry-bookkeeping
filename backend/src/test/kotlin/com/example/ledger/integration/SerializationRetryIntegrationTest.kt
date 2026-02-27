package com.example.ledger.integration

import com.example.ledger.config.CannotSerializeTransactionException
import com.example.ledger.domain.model.Account
import com.example.ledger.domain.model.AccountCategory
import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.CostBasisLot
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.JournalEntry
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.port.AccountRepository
import com.example.ledger.domain.port.JournalRepository
import com.example.ledger.domain.service.AuditService
import com.example.ledger.domain.service.FifoService
import com.example.ledger.domain.service.GainLossService
import com.example.ledger.domain.service.LedgerService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import kotlin.test.assertEquals

class SerializationRetryIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var ledgerService: LedgerService

    @MockBean
    private lateinit var journalRepository: JournalRepository

    @MockBean
    private lateinit var accountRepository: AccountRepository

    @MockBean
    private lateinit var fifoService: FifoService

    @MockBean
    private lateinit var gainLossService: GainLossService

    @MockBean
    private lateinit var auditService: AuditService

    @Test
    fun `generateEntries retries on serialization failure and succeeds`() {
        val walletAddress = "0xffffffffffffffffffffffffffffffffffffffff"
        val entryDate = Instant.parse("2026-02-10T00:00:00Z")
        val event = AccountingEvent(
            id = 101L,
            rawTransactionId = 55L,
            eventType = EventType.INCOMING,
            classifierId = "test",
            tokenSymbol = "ETH",
            amountRaw = BigInteger("1000000000000000000"),
            amountDecimal = BigDecimal("1.0"),
            priceKrw = BigDecimal("3000000"),
            priceSource = PriceSource.COINGECKO
        )

        whenever(accountRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1)
        whenever(accountRepository.findByCode(eq("자산:암호화폐:ETH"))).thenReturn(
            Account(
                id = 1L,
                code = "자산:암호화폐:ETH",
                name = "ETH 보유 자산",
                category = AccountCategory.ASSET,
                system = true
            )
        )
        whenever(fifoService.addLot(any(), any(), any(), any(), anyOrNull(), any())).thenReturn(
            CostBasisLot(
                id = 1L,
                walletAddress = walletAddress,
                tokenSymbol = "ETH",
                acquisitionDate = entryDate,
                quantity = BigDecimal("1.0"),
                remainingQuantity = BigDecimal("1.0"),
                unitCostKrw = BigDecimal("3000000"),
                rawTransactionId = 55L
            )
        )

        var saveAttempts = 0
        whenever(journalRepository.save(any())).thenAnswer { invocation ->
            saveAttempts += 1
            if (saveAttempts == 1) {
                throw CannotSerializeTransactionException("serialization failure")
            }
            val entry = invocation.getArgument<JournalEntry>(0)
            entry.copy(id = 999L)
        }

        val result = ledgerService.generateEntries(
            walletAddress = walletAddress,
            rawTransactionId = 55L,
            events = listOf(event),
            entryDate = entryDate
        )

        assertEquals(1, result.size)
        assertEquals(999L, result.first().id)
        verify(journalRepository, times(2)).save(any())
    }
}
