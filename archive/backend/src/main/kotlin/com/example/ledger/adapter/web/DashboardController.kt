package com.example.ledger.adapter.web

import com.example.ledger.application.dto.BalanceDashboardResponse
import com.example.ledger.application.usecase.DashboardUseCase
import com.example.ledger.domain.model.JournalStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val dashboardUseCase: DashboardUseCase
) {

    @GetMapping("/balances")
    fun getBalances(
        @RequestParam(required = false) walletAddress: String?,
        @RequestParam(required = false) status: JournalStatus?
    ): BalanceDashboardResponse {
        return dashboardUseCase.getBalances(walletAddress, status)
    }
}
