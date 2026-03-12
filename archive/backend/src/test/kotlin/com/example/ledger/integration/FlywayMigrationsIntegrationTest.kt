package com.example.ledger.integration

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FlywayMigrationsIntegrationTest : IntegrationTestBase() {

    @Test
    fun `migration V7, V8, and V9 should apply cutoff tables, token precision, and token identity columns`() {
        val v7Count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '7' AND success = true",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, v7Count)

        val v8Count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '8' AND success = true",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, v8Count)

        val v9Count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '9' AND success = true",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, v9Count)

        val unspecifiedIncomeCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM accounts WHERE code = '수익:미지정수입' AND is_system = true",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, unspecifiedIncomeCount)

        val externalAssetCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM accounts WHERE code = '자산:외부' AND is_system = true",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, externalAssetCount)

        val trackedTokenTableCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'wallet_tracked_tokens'",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, trackedTokenTableCount)

        val snapshotTableCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'wallet_balance_snapshots'",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, snapshotTableCount)

        val tokenQuantityPrecision = jdbcTemplate.queryForObject(
            """
            SELECT numeric_precision
            FROM information_schema.columns
            WHERE table_name = 'journal_lines' AND column_name = 'token_quantity'
            """.trimIndent(),
            Long::class.java
        ) ?: 0L
        assertEquals(78L, tokenQuantityPrecision)

        val tokenMetadataTableCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'token_metadata'",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, tokenMetadataTableCount)

        val journalLineChainCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_name = 'journal_lines' AND column_name = 'chain'
            """.trimIndent(),
            Long::class.java
        ) ?: 0L
        assertEquals(1L, journalLineChainCount)

        val journalLineTokenAddressCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_name = 'journal_lines' AND column_name = 'token_address'
            """.trimIndent(),
            Long::class.java
        ) ?: 0L
        assertEquals(1L, journalLineTokenAddressCount)

        val costBasisLotChainCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_name = 'cost_basis_lots' AND column_name = 'chain'
            """.trimIndent(),
            Long::class.java
        ) ?: 0L
        assertEquals(1L, costBasisLotChainCount)

        val costBasisLotTokenAddressCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_name = 'cost_basis_lots' AND column_name = 'token_address'
            """.trimIndent(),
            Long::class.java
        ) ?: 0L
        assertEquals(1L, costBasisLotTokenAddressCount)
    }
}
