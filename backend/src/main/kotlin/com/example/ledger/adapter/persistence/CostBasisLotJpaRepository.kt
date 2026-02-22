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

    override fun findOpenLots(walletAddress: String, tokenSymbol: String): List<CostBasisLot> {
        return springDataCostBasisLotRepository
            .findByWalletAddressAndTokenSymbolAndRemainingQtyGreaterThanOrderByAcquisitionDateAsc(
                walletAddress,
                tokenSymbol,
                BigDecimal.ZERO
            )
            .map { it.toDomain() }
    }
}
