package com.example.ledger.domain.service

import com.example.ledger.config.CannotSerializeTransactionException
import com.example.ledger.config.SerializableTx
import com.example.ledger.domain.model.CostBasisLot
import com.example.ledger.domain.port.CostBasisLotRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.CannotAcquireLockException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant

@Service
class FifoService(
    private val costBasisLotRepository: CostBasisLotRepository
) {
    private val logger = LoggerFactory.getLogger(FifoService::class.java)

    data class ConsumptionResult(
        val totalCostKrw: BigDecimal,
        val missingQuantity: BigDecimal,
        val consumedQuantity: BigDecimal
    )

    @SerializableTx
    fun addLot(
        walletAddress: String,
        tokenSymbol: String,
        quantity: BigDecimal,
        unitCostKrw: BigDecimal,
        rawTransactionId: Long?,
        acquisitionDate: Instant
    ): CostBasisLot {
        return costBasisLotRepository.save(
            CostBasisLot(
                walletAddress = walletAddress,
                tokenSymbol = tokenSymbol,
                acquisitionDate = acquisitionDate,
                quantity = quantity,
                remainingQuantity = quantity,
                unitCostKrw = unitCostKrw.setScale(8, RoundingMode.HALF_UP),
                rawTransactionId = rawTransactionId
            )
        )
    }

    @SerializableTx
    @Retryable(
        retryFor = [CannotSerializeTransactionException::class, CannotAcquireLockException::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 50, multiplier = 2.0, maxDelay = 1000)
    )
    fun consume(
        walletAddress: String,
        tokenSymbol: String,
        quantity: BigDecimal
    ): ConsumptionResult {
        var remaining = quantity
        var totalCost = BigDecimal.ZERO

        val openLots = costBasisLotRepository.findOpenLots(walletAddress, tokenSymbol)
        val updatedLots = mutableListOf<CostBasisLot>()

        for (lot in openLots) {
            if (remaining <= BigDecimal.ZERO) break
            val available = lot.remainingQuantity
            if (available <= BigDecimal.ZERO) continue

            val consumed = if (remaining < available) remaining else available
            val newLot = lot.consume(consumed)

            totalCost = totalCost.add(consumed.multiply(lot.unitCostKrw, MathContext.DECIMAL128))
            remaining = remaining.subtract(consumed)
            updatedLots += newLot
        }

        if (updatedLots.isNotEmpty()) {
            costBasisLotRepository.saveAll(updatedLots)
        }

        return ConsumptionResult(
            totalCostKrw = totalCost.setScale(8, RoundingMode.HALF_UP),
            missingQuantity = remaining.coerceAtLeast(BigDecimal.ZERO),
            consumedQuantity = quantity.subtract(remaining.coerceAtLeast(BigDecimal.ZERO))
        )
    }

    @Recover
    fun recoverAfterSerializationFailure(
        ex: CannotSerializeTransactionException,
        walletAddress: String,
        tokenSymbol: String,
        quantity: BigDecimal
    ): ConsumptionResult {
        logger.error(
            "FIFO consume retries exhausted due to serialization failures. walletAddress={}, tokenSymbol={}, quantity={}",
            walletAddress, tokenSymbol, quantity, ex
        )
        throw ex
    }

    @Recover
    fun recoverAfterLockFailure(
        ex: CannotAcquireLockException,
        walletAddress: String,
        tokenSymbol: String,
        quantity: BigDecimal
    ): ConsumptionResult {
        logger.error(
            "FIFO consume retries exhausted due to lock failures. walletAddress={}, tokenSymbol={}, quantity={}",
            walletAddress, tokenSymbol, quantity, ex
        )
        throw ex
    }
}
