package com.example.ledger.adapter.web

import com.example.ledger.application.dto.ManualClassifyRequest
import com.example.ledger.application.dto.JournalResponse
import com.example.ledger.application.usecase.JournalUseCase
import com.example.ledger.domain.model.AccountingEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/unclassified")
class UnclassifiedController(
    private val journalUseCase: JournalUseCase
) {

    @GetMapping
    fun listUnclassified(): List<AccountingEvent> {
        return journalUseCase.listUnclassified()
    }

    @PostMapping("/{id}/classify")
    fun classify(@PathVariable id: Long, @RequestBody request: ManualClassifyRequest): JournalResponse {
        return journalUseCase.manualClassify(
            eventId = id,
            eventType = request.eventType,
            tokenSymbol = request.tokenSymbol,
            amountDecimal = request.amountDecimal,
            tokenAddress = request.tokenAddress
        )
    }
}
