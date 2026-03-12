package com.example.ledger.domain.classifier

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.DecodedTransaction
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.port.ClassifierPlugin
import org.springframework.stereotype.Component
import java.math.BigInteger

@Component
class UnclassifiedFallback : ClassifierPlugin {
    override fun supports(decodedTx: DecodedTransaction): Boolean = true

    override fun classify(decodedTx: DecodedTransaction): List<AccountingEvent> {
        return listOf(
            AccountingEvent(
                rawTransactionId = decodedTx.rawTransaction.id ?: 0L,
                eventType = EventType.UNCLASSIFIED,
                classifierId = id(),
                tokenSymbol = null,
                amountRaw = BigInteger.ZERO,
                amountDecimal = java.math.BigDecimal.ZERO,
                metadata = mapOf("reason" to "no classifier matched")
            )
        )
    }

    override fun id(): String = "UNCLASSIFIED_FALLBACK"
}
