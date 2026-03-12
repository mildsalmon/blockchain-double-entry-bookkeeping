package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.CostBasisLotEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataCostBasisLotRepository : JpaRepository<CostBasisLotEntity, Long> {
    fun findByWalletAddressAndTokenSymbolAndRemainingQtyGreaterThanOrderByAcquisitionDateAsc(
        walletAddress: String,
        tokenSymbol: String,
        remainingQty: java.math.BigDecimal
    ): List<CostBasisLotEntity>

    fun findByWalletAddressAndTokenSymbolAndChainIsNullAndTokenAddressIsNullAndRemainingQtyGreaterThanOrderByAcquisitionDateAsc(
        walletAddress: String,
        tokenSymbol: String,
        remainingQty: java.math.BigDecimal
    ): List<CostBasisLotEntity>

    fun findByWalletAddressAndChainAndTokenAddressAndRemainingQtyGreaterThanOrderByAcquisitionDateAsc(
        walletAddress: String,
        chain: String,
        tokenAddress: String,
        remainingQty: java.math.BigDecimal
    ): List<CostBasisLotEntity>
}
