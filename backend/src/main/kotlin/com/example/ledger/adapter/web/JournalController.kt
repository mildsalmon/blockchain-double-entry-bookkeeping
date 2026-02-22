package com.example.ledger.adapter.web

import com.example.ledger.application.dto.BulkApproveRequest
import com.example.ledger.application.dto.JournalDetailResponse
import com.example.ledger.application.dto.JournalFilterRequest
import com.example.ledger.application.dto.JournalLineRequest
import com.example.ledger.application.dto.JournalResponse
import com.example.ledger.application.dto.JournalUpdateRequest
import com.example.ledger.application.usecase.JournalUseCase
import com.example.ledger.domain.model.JournalLine
import com.example.ledger.domain.model.JournalStatus
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/journals")
class JournalController(
    private val journalUseCase: JournalUseCase
) {
    @GetMapping
    fun listJournals(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: LocalDate?,
        @RequestParam(required = false) status: JournalStatus?,
        @RequestParam(required = false) accountCode: String?,
        @RequestParam(required = false) walletAddress: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): List<JournalResponse> {
        return journalUseCase.list(
            JournalFilterRequest(
                fromDate = fromDate,
                toDate = toDate,
                status = status,
                accountCode = accountCode,
                walletAddress = walletAddress,
                page = page,
                size = size
            )
        )
    }

    @GetMapping("/{id}")
    fun getJournal(@PathVariable id: Long): JournalDetailResponse {
        return journalUseCase.get(id)
    }

    @PatchMapping("/{id}")
    fun updateJournal(@PathVariable id: Long, @RequestBody request: JournalUpdateRequest): JournalResponse {
        return journalUseCase.update(id, request.lines.map { it.toDomain() }, request.memo)
    }

    @PostMapping("/{id}/approve")
    fun approveJournal(@PathVariable id: Long): JournalResponse {
        return journalUseCase.approve(id)
    }

    @PostMapping("/bulk-approve")
    fun bulkApprove(@RequestBody request: BulkApproveRequest): List<JournalResponse> {
        return journalUseCase.bulkApprove(request.ids)
    }

    private fun JournalLineRequest.toDomain(): JournalLine {
        return JournalLine(
            accountCode = accountCode,
            debitAmount = debitAmount,
            creditAmount = creditAmount,
            tokenSymbol = tokenSymbol,
            tokenQuantity = tokenQuantity
        )
    }
}
