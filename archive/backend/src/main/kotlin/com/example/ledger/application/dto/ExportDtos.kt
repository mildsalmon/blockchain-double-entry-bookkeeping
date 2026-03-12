package com.example.ledger.application.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

enum class ExportFormat {
    CSV,
    XLSX
}

data class ExportRequest(
    @field:NotNull
    val fromDate: LocalDate,
    @field:NotNull
    val toDate: LocalDate,
    @field:NotNull
    val format: ExportFormat,
    val walletAddress: String? = null
)
