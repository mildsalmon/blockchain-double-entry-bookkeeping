package com.example.ledger.adapter.export

import com.example.ledger.domain.model.JournalEntry
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

@Component
class ExcelExporter {
    fun export(entries: List<JournalEntry>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("journals")

        val header = sheet.createRow(0)
        val columns = listOf("날짜", "설명", "차변계정", "대변계정", "차변금액", "대변금액", "토큰", "토큰수량", "상태")
        columns.forEachIndexed { index, name ->
            header.createCell(index).setCellValue(name)
        }

        entries.forEachIndexed { idx, entry ->
            val row = sheet.createRow(idx + 1)
            val debitLine = entry.lines.find { it.debitAmount > java.math.BigDecimal.ZERO }
            val creditLine = entry.lines.find { it.creditAmount > java.math.BigDecimal.ZERO }
            row.createCell(0).setCellValue(entry.entryDate.toString())
            row.createCell(1).setCellValue(entry.description)
            row.createCell(2).setCellValue(debitLine?.accountCode ?: "")
            row.createCell(3).setCellValue(creditLine?.accountCode ?: "")
            row.createCell(4).setCellValue(debitLine?.debitAmount?.toDouble() ?: 0.0)
            row.createCell(5).setCellValue(creditLine?.creditAmount?.toDouble() ?: 0.0)
            row.createCell(6).setCellValue(debitLine?.tokenSymbol ?: creditLine?.tokenSymbol ?: "")
            row.createCell(7).setCellValue((debitLine?.tokenQuantity ?: creditLine?.tokenQuantity ?: java.math.BigDecimal.ZERO).toDouble())
            row.createCell(8).setCellValue(entry.status.name)
        }

        columns.indices.forEach { sheet.autoSizeColumn(it) }

        val output = ByteArrayOutputStream()
        workbook.use { wb ->
            wb.write(output)
        }
        return output.toByteArray()
    }
}
