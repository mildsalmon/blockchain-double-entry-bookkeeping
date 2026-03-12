package com.example.ledger.domain.model

import java.math.BigDecimal

sealed interface DecodedEvent

data class TransferEvent(
    val tokenAddress: String?,
    val tokenSymbol: String,
    val from: String,
    val to: String,
    val amount: BigDecimal
) : DecodedEvent

data class SwapEvent(
    val protocol: String,
    val tokenInAddress: String?,
    val tokenInSymbol: String,
    val amountIn: BigDecimal,
    val tokenOutAddress: String?,
    val tokenOutSymbol: String,
    val amountOut: BigDecimal
) : DecodedEvent

data class DecodedTransaction(
    val rawTransaction: RawTransaction,
    val from: String?,
    val to: String?,
    val nativeValue: BigDecimal,
    val gasUsedEth: BigDecimal,
    val events: List<DecodedEvent>
)
