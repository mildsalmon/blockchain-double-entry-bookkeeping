package com.example.ledger.domain.classifier

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.DecodedTransaction
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.port.ClassifierPlugin
import org.springframework.stereotype.Component
import java.math.BigInteger

@Component
class EthTransferClassifier : ClassifierPlugin {
    override fun supports(decodedTx: DecodedTransaction): Boolean {
        return decodedTx.nativeValue > java.math.BigDecimal.ZERO
    }

    override fun classify(decodedTx: DecodedTransaction): List<AccountingEvent> {
        if (!supports(decodedTx)) return emptyList()

        val walletAddress = decodedTx.rawTransaction.walletAddress.lowercase()
        val eventType = when {
            decodedTx.to?.lowercase() == walletAddress -> EventType.INCOMING
            decodedTx.from?.lowercase() == walletAddress -> EventType.OUTGOING
            else -> EventType.UNCLASSIFIED
        }

        return listOf(
            AccountingEvent(
                rawTransactionId = decodedTx.rawTransaction.id ?: 0L,
                eventType = eventType,
                classifierId = id(),
                tokenSymbol = "ETH",
                amountRaw = decodedTx.nativeValue.toBigInteger(),
                amountDecimal = decodedTx.nativeValue,
                counterparty = if (eventType == EventType.INCOMING) decodedTx.from else decodedTx.to
            )
        )
    }

    override fun id(): String = "ETH_TRANSFER"
}
