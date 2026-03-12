package com.example.ledger.domain.service

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.DecodedTransaction
import com.example.ledger.domain.port.ClassifierPlugin
import org.springframework.stereotype.Service

@Service
class ClassificationService(
    private val plugins: List<ClassifierPlugin>
) {
    fun classify(decodedTransaction: DecodedTransaction): List<AccountingEvent> {
        val orderedPlugins = plugins.sortedBy { if (it.id() == "UNCLASSIFIED_FALLBACK") 1 else 0 }
        val events = mutableListOf<AccountingEvent>()

        orderedPlugins
            .filter { it.id() != "UNCLASSIFIED_FALLBACK" }
            .filter { it.supports(decodedTransaction) }
            .forEach { plugin ->
                events += plugin.classify(decodedTransaction)
            }

        if (events.isEmpty()) {
            val fallback = orderedPlugins.firstOrNull { it.id() == "UNCLASSIFIED_FALLBACK" }
            if (fallback != null && fallback.supports(decodedTransaction)) {
                events += fallback.classify(decodedTransaction)
            }
        }

        return events
    }
}
