package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.spring.SpringDataCostBasisLotRepository
import com.example.ledger.domain.model.CostBasisLot
import com.example.ledger.domain.port.CostBasisLotRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class CostBasisLotJpaRepository(
    private val springDataCostBasisLotRepository: SpringDataCostBasisLotRepository
) : CostBasisLotRepository {
    override fun save(lot: CostBasisLot): CostBasisLot {
        return springDataCostBasisLotRepository.save(lot.toEntity()).toDomain()
    }

    override fun saveAll(lots: List<CostBasisLot>): List<CostBasisLot> {
        if (lots.isEmpty()) return emptyList()
        return springDataCostBasisLotRepository.saveAll(lots.map { it.toEntity() }).map { it.toDomain() }
    }

    override fun findOpenLots(walletAddress: String, tokenSymbol: String, chain: String?, tokenAddress: String?): List<CostBasisLot> {
        val rows = if (!chain.isNullOrBlank() && !tokenAddress.isNullOrBlank()) {
            val exactRows = springDataCostBasisLotRepository
                .findByWalletAddressAndChainAndTokenAddressAndRemainingQtyGreaterThanOrderByAcquisitionDateAsc(
                    walletAddress,
                    chain,
                    tokenAddress,
                    BigDecimal.ZERO
                )
            val legacyRows = springDataCostBasisLotRepository
                .findByWalletAddressAndTokenSymbolAndChainIsNullAndTokenAddressIsNullAndRemainingQtyGreaterThanOrderByAcquisitionDateAsc(
                    walletAddress,
                    tokenSymbol,
                    BigDecimal.ZERO
                )
            (exactRows + legacyRows).sortedWith(compareBy({ it.acquisitionDate }, { it.id ?: Long.MAX_VALUE }))
        } else if (tokenSymbol == "ETH") {
            springDataCostBasisLotRepository
                .findByWalletAddressAndTokenSymbolAndChainIsNullAndTokenAddressIsNullAndRemainingQtyGreaterThanOrderByAcquisitionDateAsc(
                    walletAddress,
                    tokenSymbol,
                    BigDecimal.ZERO
                )
        } else {
            springDataCostBasisLotRepository
                .findByWalletAddressAndTokenSymbolAndRemainingQtyGreaterThanOrderByAcquisitionDateAsc(
                    walletAddress,
                    tokenSymbol,
                    BigDecimal.ZERO
                )
        }
        return rows.map { it.toDomain() }
    }
}
