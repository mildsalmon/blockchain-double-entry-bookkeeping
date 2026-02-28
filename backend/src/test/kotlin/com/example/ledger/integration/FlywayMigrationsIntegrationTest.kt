package com.example.ledger.integration

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FlywayMigrationsIntegrationTest : IntegrationTestBase() {

    @Test
    fun `migration V7 should exist and create cutoff tables`() {
        val v7Count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '7' AND success = true",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, v7Count)

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
    }
}
