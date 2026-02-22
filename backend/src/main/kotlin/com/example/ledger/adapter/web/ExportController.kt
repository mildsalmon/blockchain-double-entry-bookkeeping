package com.example.ledger.adapter.web

import com.example.ledger.application.dto.ExportFormat
import com.example.ledger.application.dto.ExportRequest
import com.example.ledger.application.usecase.ExportUseCase
import jakarta.validation.Valid
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/export")
class ExportController(
    private val exportUseCase: ExportUseCase
) {
    @PostMapping
    fun export(@Valid @RequestBody request: ExportRequest): ResponseEntity<ByteArray> {
        val bytes = exportUseCase.export(request)
        val filename = "journals_${request.fromDate}_${request.toDate}.${if (request.format == ExportFormat.CSV) "csv" else "xlsx"}"

        val contentType = if (request.format == ExportFormat.CSV) {
            MediaType("text", "csv")
        } else {
            MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }

        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .body(bytes)
    }
}
