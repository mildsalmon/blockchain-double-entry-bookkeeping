package com.example.ledger.domain.port

import com.example.ledger.domain.model.RawTransaction
import java.math.BigInteger

interface BlockchainDataPort {
    fun fetchTransactions(walletAddress: String, fromBlock: Long? = null): List<RawTransaction>
    fun getNativeBalanceAtBlock(walletAddress: String, blockNumber: Long): BigInteger
    fun getTokenBalanceAtBlock(walletAddress: String, tokenAddress: String, blockNumber: Long): BigInteger
}
