package com.example.ledger.adapter.web

import com.example.ledger.application.dto.WalletCreateRequest
import com.example.ledger.application.dto.WalletAdminCorrectionApplyRequest
import com.example.ledger.application.dto.WalletAdminCorrectionPreflightRequest
import com.example.ledger.application.dto.WalletAdminCorrectionPreflightResponse
import com.example.ledger.application.dto.WalletCutoffPreflightRequest
import com.example.ledger.application.dto.WalletCutoffPreflightResponse
import com.example.ledger.application.dto.WalletResponse
import com.example.ledger.application.dto.WalletStatusResponse
import com.example.ledger.application.usecase.WalletAdminCorrectionUseCase
import com.example.ledger.application.usecase.IngestWalletUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/wallets")
class WalletController(
    private val ingestWalletUseCase: IngestWalletUseCase,
    private val walletAdminCorrectionUseCase: WalletAdminCorrectionUseCase
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createWallet(@Valid @RequestBody request: WalletCreateRequest): WalletResponse {
        return ingestWalletUseCase.registerWallet(
            address = request.address,
            label = request.label,
            reviewedBy = request.reviewedBy,
            preflightSummaryHash = request.preflightSummaryHash,
            mode = request.mode,
            cutoffBlock = request.cutoffBlock,
            startBlock = request.startBlock,
            trackedTokens = request.trackedTokens
        )
    }

    @PostMapping("/cutoff-preflight")
    fun preflightCutoffWallet(
        @Valid @RequestBody request: WalletCutoffPreflightRequest
    ): WalletCutoffPreflightResponse {
        return ingestWalletUseCase.preflightCutoffWallet(
            address = request.address,
            cutoffBlock = request.cutoffBlock,
            trackedTokens = request.trackedTokens
        )
    }

    @PostMapping("/{address}/admin-corrections/preflight")
    fun preflightAdminCorrection(
        @PathVariable address: String,
        principal: Principal,
        @Valid @RequestBody request: WalletAdminCorrectionPreflightRequest
    ): WalletAdminCorrectionPreflightResponse {
        return walletAdminCorrectionUseCase.preflight(
            address = address,
            tokenAddresses = request.tokenAddresses,
            actor = principal.name,
            approvalReference = request.approvalReference,
            reason = request.reason
        )
    }

    @PostMapping("/{address}/admin-corrections/apply")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun applyAdminCorrection(
        @PathVariable address: String,
        principal: Principal,
        @Valid @RequestBody request: WalletAdminCorrectionApplyRequest
    ): WalletResponse {
        return walletAdminCorrectionUseCase.apply(
            address = address,
            tokenAddresses = request.tokenAddresses,
            actor = principal.name,
            approvalReference = request.approvalReference,
            reason = request.reason,
            summaryHash = request.summaryHash
        )
    }

    @GetMapping
    fun listWallets(): List<WalletResponse> {
        return ingestWalletUseCase.listWallets()
    }

    @GetMapping("/{address}/status")
    fun getWalletStatus(@PathVariable address: String): WalletStatusResponse {
        return ingestWalletUseCase.getStatus(address)
    }

    @PostMapping("/{address}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retryWalletSync(@PathVariable address: String): WalletResponse {
        return ingestWalletUseCase.retrySync(address)
    }

    @DeleteMapping("/{address}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteWallet(@PathVariable address: String) {
        ingestWalletUseCase.deleteWallet(address)
    }
}
