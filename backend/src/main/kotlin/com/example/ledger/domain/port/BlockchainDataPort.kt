package com.example.ledger.domain.port

import com.example.ledger.domain.model.RawTransaction

interface BlockchainDataPort {
    fun fetchTransactions(walletAddress: String, fromBlock: Long? = null): List<RawTransaction>
}
