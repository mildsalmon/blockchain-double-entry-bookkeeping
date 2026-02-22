package com.example.ledger.application.dto

import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.PriceSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class JournalLineRequest(
    val accountCode: String,
    val debitAmount: BigDecimal,
    val creditAmount: BigDecimal,
    val tokenSymbol: String? = null,
    val tokenQuantity: BigDecimal? = null
)

data class JournalUpdateRequest(
    val lines: List<JournalLineRequest>,
    val memo: String? = null
)

data class BulkApproveRequest(
    val ids: List<Long>
)

data class ManualClassifyRequest(
    val eventType: EventType,
    val tokenSymbol: String,
    val amountDecimal: BigDecimal,
    val tokenAddress: String? = null
)

data class JournalLineResponse(
    val id: Long?,
    val accountCode: String,
    val debitAmount: BigDecimal,
    val creditAmount: BigDecimal,
    val tokenSymbol: String?,
    val tokenQuantity: BigDecimal?
)

data class JournalResponse(
    val id: Long?,
    val rawTransactionId: Long,
    val entryDate: Instant,
    val description: String,
    val status: JournalStatus,
    val memo: String?,
    val lines: List<JournalLineResponse>
)

data class JournalDetailResponse(
    val journal: JournalResponse,
    val txHash: String?,
    val classifierId: String?,
    val priceSource: PriceSource?
)

data class JournalFilterRequest(
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val status: JournalStatus? = null,
    val accountCode: String? = null,
    val walletAddress: String? = null,
    val page: Int = 0,
    val size: Int = 50
)
