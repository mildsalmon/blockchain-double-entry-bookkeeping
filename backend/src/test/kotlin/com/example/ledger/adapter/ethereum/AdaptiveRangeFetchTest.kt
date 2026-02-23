package com.example.ledger.adapter.ethereum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdaptiveRangeFetchTest {

    @Test
    fun `splits block range when provider returns too many results error`() {
        val calls = mutableListOf<Pair<Long, Long>>()

        val result = fetchWithAdaptiveRange(
            fromBlock = 0L,
            toBlock = 9L,
            fetch = { from, to ->
                calls += Pair(from, to)
                if (from == 0L && to == 9L) {
                    throw EthereumRpcException(-32005, "query returned more than 10000 results")
                }
                listOf("$from-$to")
            },
            shouldSplit = { it is EthereumRpcException && isTooManyResultsError(it) }
        )

        assertEquals(listOf(0L to 9L, 0L to 4L, 5L to 9L), calls)
        assertEquals(listOf("0-4", "5-9"), result)
    }

    @Test
    fun `throws when single block still exceeds provider limit`() {
        assertFailsWith<EthereumRpcException> {
            fetchWithAdaptiveRange<String>(
                fromBlock = 123L,
                toBlock = 123L,
                fetch = { _: Long, _: Long -> throw EthereumRpcException(-32005, "too many results") },
                shouldSplit = { it is EthereumRpcException && isTooManyResultsError(it) }
            )
        }
    }
}
