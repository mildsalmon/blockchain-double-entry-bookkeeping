package com.example.ledger.adapter.web

import com.example.ledger.application.dto.WalletCreateRequest
import com.example.ledger.application.dto.WalletResponse
import com.example.ledger.application.dto.WalletStatusResponse
import com.example.ledger.application.usecase.IngestWalletUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wallets")
class WalletController(
    private val ingestWalletUseCase: IngestWalletUseCase
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createWallet(@Valid @RequestBody request: WalletCreateRequest): WalletResponse {
        return ingestWalletUseCase.registerWallet(request.address, request.label, request.startBlock)
    }

    @GetMapping
    fun listWallets(): List<WalletResponse> {
        return ingestWalletUseCase.listWallets()
    }

    @GetMapping("/{address}/status")
    fun getWalletStatus(@PathVariable address: String): WalletStatusResponse {
        return ingestWalletUseCase.getStatus(address)
    }
}
