package com.example.ledger.domain.port

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.DecodedTransaction

interface ClassifierPlugin {
    fun supports(decodedTx: DecodedTransaction): Boolean
    fun classify(decodedTx: DecodedTransaction): List<AccountingEvent>
    fun id(): String
}
