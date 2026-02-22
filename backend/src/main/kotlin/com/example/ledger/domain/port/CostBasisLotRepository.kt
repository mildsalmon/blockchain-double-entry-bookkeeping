package com.example.ledger.domain.port

import com.example.ledger.domain.model.CostBasisLot

interface CostBasisLotRepository {
    fun save(lot: CostBasisLot): CostBasisLot
    fun saveAll(lots: List<CostBasisLot>): List<CostBasisLot>
    fun findOpenLots(walletAddress: String, tokenSymbol: String): List<CostBasisLot>
}
