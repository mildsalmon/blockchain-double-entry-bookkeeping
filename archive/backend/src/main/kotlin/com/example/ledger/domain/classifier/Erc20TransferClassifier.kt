package com.example.ledger.domain.classifier

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.DecodedTransaction
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.TransferEvent
import com.example.ledger.domain.port.ClassifierPlugin
import org.springframework.stereotype.Component

@Component
class Erc20TransferClassifier : ClassifierPlugin {
    override fun supports(decodedTx: DecodedTransaction): Boolean {
        return decodedTx.events.any { it is TransferEvent }
    }

    override fun classify(decodedTx: DecodedTransaction): List<AccountingEvent> {
        val walletAddress = decodedTx.rawTransaction.walletAddress.lowercase()

        return decodedTx.events.filterIsInstance<TransferEvent>().map { event ->
            val eventType = when {
                event.to.lowercase() == walletAddress -> EventType.INCOMING
                event.from.lowercase() == walletAddress -> EventType.OUTGOING
                else -> EventType.UNCLASSIFIED
            }

            AccountingEvent(
                rawTransactionId = decodedTx.rawTransaction.id ?: 0L,
                eventType = eventType,
                classifierId = id(),
                tokenAddress = event.tokenAddress,
                tokenSymbol = event.tokenSymbol,
                amountRaw = event.amount.toBigInteger(),
                amountDecimal = event.amount,
                counterparty = if (eventType == EventType.INCOMING) event.from else event.to
            )
        }
    }

    override fun id(): String = "ERC20_TRANSFER"
}
