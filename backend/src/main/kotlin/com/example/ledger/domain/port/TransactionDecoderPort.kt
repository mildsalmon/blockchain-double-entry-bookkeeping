package com.example.ledger.domain.port

import com.example.ledger.domain.model.DecodedTransaction
import com.example.ledger.domain.model.RawTransaction

interface TransactionDecoderPort {
    fun decode(rawTransaction: RawTransaction): DecodedTransaction
}
