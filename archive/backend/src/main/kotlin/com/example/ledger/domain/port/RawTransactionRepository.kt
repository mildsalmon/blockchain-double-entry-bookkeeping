package com.example.ledger.domain.port

import com.example.ledger.domain.model.RawTransaction

interface RawTransactionRepository {
    fun saveAll(transactions: List<RawTransaction>): List<RawTransaction>
    fun findByWalletAddress(walletAddress: String): List<RawTransaction>
    fun existsByTxHash(txHash: String): Boolean
    fun findById(id: Long): RawTransaction?
}
