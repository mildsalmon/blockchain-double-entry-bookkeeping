package com.example.ledger.adapter.export

import com.example.ledger.domain.model.JournalEntry
import com.opencsv.CSVWriter
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@Component
class CsvExporter {
    fun export(entries: List<JournalEntry>): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

        OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
            CSVWriter(writer).use { csv ->
                csv.writeNext(arrayOf("날짜", "설명", "차변계정", "대변계정", "차변금액", "대변금액", "토큰", "토큰수량", "상태"))

                entries.forEach { entry ->
                    val debitLine = entry.lines.find { it.debitAmount > java.math.BigDecimal.ZERO }
                    val creditLine = entry.lines.find { it.creditAmount > java.math.BigDecimal.ZERO }
                    csv.writeNext(
                        arrayOf(
                            entry.entryDate.toString(),
                            entry.description,
                            debitLine?.accountCode.orEmpty(),
                            creditLine?.accountCode.orEmpty(),
                            debitLine?.debitAmount?.toPlainString().orEmpty(),
                            creditLine?.creditAmount?.toPlainString().orEmpty(),
                            debitLine?.tokenSymbol ?: creditLine?.tokenSymbol ?: "",
                            debitLine?.tokenQuantity?.toPlainString() ?: creditLine?.tokenQuantity?.toPlainString() ?: "",
                            entry.status.name
                        )
                    )
                }
            }
        }

        return output.toByteArray()
    }
}
