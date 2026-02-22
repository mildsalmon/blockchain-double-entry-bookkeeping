package com.example.ledger.domain.classifier

import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.DecodedTransaction
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.SwapEvent
import com.example.ledger.domain.port.ClassifierPlugin
import org.springframework.stereotype.Component

@Component
class UniswapV2Classifier : ClassifierPlugin {
    override fun supports(decodedTx: DecodedTransaction): Boolean {
        return decodedTx.events.filterIsInstance<SwapEvent>().any { it.protocol.equals("uniswap_v2", ignoreCase = true) }
    }

    override fun classify(decodedTx: DecodedTransaction): List<AccountingEvent> {
        return decodedTx.events.filterIsInstance<SwapEvent>()
            .filter { it.protocol.equals("uniswap_v2", ignoreCase = true) }
            .map { swap ->
                AccountingEvent(
                    rawTransactionId = decodedTx.rawTransaction.id ?: 0L,
                    eventType = EventType.SWAP,
                    classifierId = id(),
                    tokenAddress = swap.tokenInAddress,
                    tokenSymbol = swap.tokenInSymbol,
                    amountRaw = swap.amountIn.toBigInteger(),
                    amountDecimal = swap.amountIn,
                    metadata = mapOf(
                        "tokenOutAddress" to swap.tokenOutAddress,
                        "tokenOutSymbol" to swap.tokenOutSymbol,
                        "amountOut" to swap.amountOut
                    )
                )
            }
    }

    override fun id(): String = "UNISWAP_V2_SWAP"
}
