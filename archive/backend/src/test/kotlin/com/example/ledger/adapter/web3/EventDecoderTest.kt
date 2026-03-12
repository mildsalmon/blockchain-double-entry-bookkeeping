package com.example.ledger.adapter.web3

import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.SwapEvent
import com.example.ledger.domain.model.TransferEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventDecoderTest {

    private val objectMapper = ObjectMapper()
    private val decoder = EventDecoder()

    @Test
    fun `decodes erc20 transfer from receipt logs`() {
        val rawData = readFixture("fixtures/erc20-transfer-receipt.json")
        val rawTransaction = rawTransaction(rawData)

        val decoded = decoder.decode(rawTransaction)
        val transfer = decoded.events.filterIsInstance<TransferEvent>().firstOrNull()

        assertNotNull(transfer)
        assertEquals("USDC", transfer.tokenSymbol)
        assertEquals("0x1111111111111111111111111111111111111111", transfer.from)
        assertEquals("0x2222222222222222222222222222222222222222", transfer.to)
        assertEquals(BigDecimal("1000000"), transfer.amount)
    }

    @Test
    fun `decodes uniswap v2 swap from receipt logs`() {
        val rawData = readFixture("fixtures/uniswap-v2-swap-receipt.json")
        val rawTransaction = rawTransaction(rawData)

        val decoded = decoder.decode(rawTransaction)
        val swap = decoded.events.filterIsInstance<SwapEvent>().firstOrNull()

        assertNotNull(swap)
        assertEquals("uniswap_v2", swap.protocol)
        assertEquals("WETH", swap.tokenInSymbol)
        assertEquals("USDC", swap.tokenOutSymbol)
        assertEquals(BigDecimal("1000000000000000000"), swap.amountIn)
        assertEquals(BigDecimal("2500000000"), swap.amountOut)
    }

    @Test
    fun `decodes uniswap v3 swap from receipt logs`() {
        val rawData = readFixture("fixtures/uniswap-v3-swap-receipt.json")
        val rawTransaction = rawTransaction(rawData)

        val decoded = decoder.decode(rawTransaction)
        val swap = decoded.events.filterIsInstance<SwapEvent>().firstOrNull()

        assertNotNull(swap)
        assertEquals("uniswap_v3", swap.protocol)
        assertTrue(swap.amountIn > BigDecimal.ZERO)
        assertTrue(swap.amountOut > BigDecimal.ZERO)
    }

    private fun readFixture(path: String) =
        objectMapper.readTree(requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "fixture not found: $path" })

    private fun rawTransaction(rawData: com.fasterxml.jackson.databind.JsonNode): RawTransaction {
        return RawTransaction(
            walletAddress = "0x1111111111111111111111111111111111111111",
            txHash = "0xdecoder-test",
            blockNumber = 123L,
            txIndex = 0,
            blockTimestamp = Instant.parse("2026-02-22T00:00:00Z"),
            rawData = rawData,
            txStatus = 1
        )
    }
}
