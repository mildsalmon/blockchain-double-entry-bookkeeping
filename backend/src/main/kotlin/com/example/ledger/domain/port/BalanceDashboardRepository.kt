package com.example.ledger.domain.port

import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.TokenBalancePosition

interface BalanceDashboardRepository {
    fun findBalancePositions(walletAddress: String? = null, status: JournalStatus? = null): List<TokenBalancePosition>
}
