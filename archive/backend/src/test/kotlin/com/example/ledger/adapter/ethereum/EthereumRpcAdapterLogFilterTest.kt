package com.example.ledger.adapter.ethereum

import com.example.ledger.adapter.ethereum.dto.LogEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EthereumRpcAdapterLogFilterTest {

    @Test
    fun `wallet topic in log should be treated as relevant`() {
        val walletTopic = "0x0000000000000000000000001111111111111111111111111111111111111111"
        val log = LogEntry(
            address = "0xToken",
            topics = listOf(
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                walletTopic.uppercase()
            ),
            data = "0x",
            blockNumber = "0x1",
            transactionHash = "0xhash",
            transactionIndex = "0x0",
            blockHash = "0xblock",
            logIndex = "0x0",
            removed = false
        )

        assertTrue(
            isWalletRelatedLog(
                log = log,
                paddedWalletTopic = walletTopic
            )
        )
    }

    @Test
    fun `swap log without wallet topic should not be treated as wallet related`() {
        val walletTopic = "0x0000000000000000000000001111111111111111111111111111111111111111"
        val log = LogEntry(
            address = "0xPool",
            topics = listOf(
                "0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67",
                "0x0000000000000000000000002222222222222222222222222222222222222222"
            ),
            data = "0x",
            blockNumber = "0x1",
            transactionHash = "0xhash",
            transactionIndex = "0x0",
            blockHash = "0xblock",
            logIndex = "0x0",
            removed = false
        )

        assertFalse(
            isWalletRelatedLog(
                log = log,
                paddedWalletTopic = walletTopic
            )
        )
    }
}
