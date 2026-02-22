package com.example.ledger.domain.classifier

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.DecodedTransaction
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.port.ClassifierPlugin
import org.springframework.stereotype.Component

@Component
class GasFeeClassifier : ClassifierPlugin {
    override fun supports(decodedTx: DecodedTransaction): Boolean {
        val walletAddress = decodedTx.rawTransaction.walletAddress.lowercase()
        return decodedTx.from?.lowercase() == walletAddress && decodedTx.gasUsedEth > java.math.BigDecimal.ZERO
    }

    override fun classify(decodedTx: DecodedTransaction): List<AccountingEvent> {
        if (!supports(decodedTx)) return emptyList()
        return listOf(
            AccountingEvent(
                rawTransactionId = decodedTx.rawTransaction.id ?: 0L,
                eventType = EventType.FEE,
                classifierId = id(),
                tokenSymbol = "ETH",
                amountRaw = decodedTx.gasUsedEth.toBigInteger(),
                amountDecimal = decodedTx.gasUsedEth,
                counterparty = null
            )
        )
    }

    override fun id(): String = "GAS_FEE"
}
