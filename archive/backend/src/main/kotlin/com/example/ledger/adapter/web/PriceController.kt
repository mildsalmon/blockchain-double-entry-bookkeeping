package com.example.ledger.adapter.web

import com.example.ledger.application.dto.PriceResponse
import com.example.ledger.domain.port.PricePort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/prices")
class PriceController(
    private val pricePort: PricePort
) {
    @GetMapping("/{token}/{date}")
    fun getPrice(
        @PathVariable token: String,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): PriceResponse {
        val info = pricePort.getPrice(null, token, date)
        return PriceResponse(
            token = token,
            date = date,
            priceKrw = info.priceKrw,
            source = info.source
        )
    }
}
