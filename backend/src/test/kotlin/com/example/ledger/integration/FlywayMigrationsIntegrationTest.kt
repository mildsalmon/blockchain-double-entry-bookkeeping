package com.example.ledger.integration

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FlywayMigrationsIntegrationTest : IntegrationTestBase() {

    @Test
    fun `migration V6 should exist and seed additional system accounts`() {
        val v6Count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '6' AND success = true",
            Long::class.java
        ) ?: 0L
        assertEquals(1L, v6Count)

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
    }
}

