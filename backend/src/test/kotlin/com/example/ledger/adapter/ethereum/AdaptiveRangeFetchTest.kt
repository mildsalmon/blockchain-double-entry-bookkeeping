package com.example.ledger.adapter.ethereum

import com.fasterxml.jackson.databind.JsonMappingException
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
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

    @Test
    fun `splits block range when provider returns query exceeds max results error`() {
        val calls = mutableListOf<Pair<Long, Long>>()

        val result = fetchWithAdaptiveRange(
            fromBlock = 24554836L,
            toBlock = 24555144L,
            fetch = { from, to ->
                calls += Pair(from, to)
                if (from == 24554836L && to == 24555144L) {
                    throw EthereumRpcException(-32602, "query exceeds max results 20000, retry with the range 24554836-24554871")
                }
                listOf("$from-$to")
            },
            shouldSplit = { it is EthereumRpcException && isTooManyResultsError(it) }
        )

        assertEquals(
            listOf(
                24554836L to 24555144L,
                24554836L to 24554990L,
                24554991L to 24555144L
            ),
            calls
        )
        assertEquals(listOf("24554836-24554990", "24554991-24555144"), result)
    }

    @Test
    fun `splits block range when provider returns request timeout`() {
        val calls = mutableListOf<Pair<Long, Long>>()

        val result = fetchWithAdaptiveRange(
            fromBlock = 10L,
            toBlock = 19L,
            fetch = { from, to ->
                calls += Pair(from, to)
                if (from == 10L && to == 19L) {
                    throw WebClientResponseException.create(
                        408,
                        "Request Timeout",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        null,
                        null
                    )
                }
                listOf("$from-$to")
            },
            shouldSplit = { shouldSplitLogRangeError(it) }
        )

        assertEquals(listOf(10L to 19L, 10L to 14L, 15L to 19L), calls)
        assertEquals(listOf("10-14", "15-19"), result)
    }

    @Test
    fun `splits block range when response body exceeds buffer limit`() {
        val calls = mutableListOf<Pair<Long, Long>>()

        val result = fetchWithAdaptiveRange(
            fromBlock = 20L,
            toBlock = 29L,
            fetch = { from, to ->
                calls += Pair(from, to)
                if (from == 20L && to == 29L) {
                    throw RuntimeException(
                        "wrapped webclient error",
                        DataBufferLimitException("Exceeded limit on max bytes to buffer : 262144")
                    )
                }
                listOf("$from-$to")
            },
            shouldSplit = { shouldSplitLogRangeError(it) }
        )

        assertEquals(listOf(20L to 29L, 20L to 24L, 25L to 29L), calls)
        assertEquals(listOf("20-24", "25-29"), result)
    }

    @Test
    fun `splits block range when provider returns truncated json`() {
        val calls = mutableListOf<Pair<Long, Long>>()

        val result = fetchWithAdaptiveRange(
            fromBlock = 30L,
            toBlock = 39L,
            fetch = { from, to ->
                calls += Pair(from, to)
                if (from == 30L && to == 39L) {
                    val truncatedJsonError = JsonMappingException.from(
                        null as com.fasterxml.jackson.core.JsonParser?,
                        "Unexpected end-of-input in field name"
                    )
                    throw RuntimeException("wrapped parser error", truncatedJsonError)
                }
                listOf("$from-$to")
            },
            shouldSplit = { shouldSplitLogRangeError(it) }
        )

        assertEquals(listOf(30L to 39L, 30L to 34L, 35L to 39L), calls)
        assertEquals(listOf("30-34", "35-39"), result)
    }
}
