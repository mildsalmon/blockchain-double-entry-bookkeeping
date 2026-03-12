package com.example.ledger.application.usecase

import com.example.ledger.adapter.export.CsvExporter
import com.example.ledger.adapter.export.ExcelExporter
import com.example.ledger.application.dto.ExportFormat
import com.example.ledger.application.dto.ExportRequest
import com.example.ledger.application.dto.JournalFilterRequest
import com.example.ledger.domain.port.JournalRepository
import org.springframework.stereotype.Service
import java.time.ZoneOffset

@Service
class ExportUseCase(
    private val journalRepository: JournalRepository,
    private val csvExporter: CsvExporter,
    private val excelExporter: ExcelExporter
) {
    fun export(request: ExportRequest): ByteArray {
        val journals = journalRepository.findByFilters(
            fromDate = request.fromDate.atStartOfDay().toInstant(ZoneOffset.UTC),
            toDate = request.toDate.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC),
            walletAddress = request.walletAddress
        )

        return when (request.format) {
            ExportFormat.CSV -> csvExporter.export(journals)
            ExportFormat.XLSX -> excelExporter.export(journals)
        }
    }
}
