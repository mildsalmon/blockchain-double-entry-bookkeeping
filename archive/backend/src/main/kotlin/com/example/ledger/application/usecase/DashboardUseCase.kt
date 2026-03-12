package com.example.ledger.application.usecase

import com.example.ledger.application.dto.BalanceDashboardResponse
import com.example.ledger.application.dto.BalancePositionResponse
import com.example.ledger.application.dto.BalanceSummaryResponse
import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.port.BalanceDashboardRepository
import com.example.ledger.domain.service.TokenMetadataService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class DashboardUseCase(
    private val balanceDashboardRepository: BalanceDashboardRepository,
    private val tokenMetadataService: TokenMetadataService
) {

    fun getBalances(walletAddress: String?, status: JournalStatus?): BalanceDashboardResponse {
        val positions = balanceDashboardRepository.findBalancePositions(walletAddress, status)
            .map { position ->
                val tokenDisplay = tokenMetadataService.resolveForRead(
                    tokenAddress = position.tokenAddress,
                    fallbackSymbol = position.tokenSymbol,
                    chain = position.chain
                )
                BalancePositionResponse(
                    walletAddress = position.walletAddress,
                    accountCode = position.accountCode,
                    tokenSymbol = tokenDisplay.tokenSymbol,
                    chain = tokenDisplay.chain,
                    chainLabel = tokenDisplay.chainLabel,
                    tokenAddress = tokenDisplay.tokenAddress,
                    displayLabel = tokenDisplay.displayLabel,
                    quantity = formatQuantity(position.quantity),
                    lastEntryDate = position.lastEntryDate
                )
            }

        return BalanceDashboardResponse(
            generatedAt = Instant.now(),
            summary = BalanceSummaryResponse(
                walletCount = positions.map { it.walletAddress }.distinct().size,
                tokenCount = positions.map { position ->
                    position.tokenAddress ?: "${position.chain}:${position.tokenSymbol}"
                }.distinct().size,
                positionCount = positions.size
            ),
            positions = positions
        )
    }

    private fun formatQuantity(quantity: BigDecimal): String {
        return quantity.stripTrailingZeros().toPlainString()
    }
}
