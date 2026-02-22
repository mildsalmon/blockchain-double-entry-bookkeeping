package com.example.ledger.integration

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanupDatabase() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              journal_lines,
              journal_entries,
              accounting_events,
              raw_transactions,
              cost_basis_lots,
              wallets,
              audit_log,
              price_cache
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbcTemplate.update("DELETE FROM accounts WHERE is_system = false")
    }

    companion object {
        @JvmStatic
        private val postgres: PostgreSQLContainer<*> = SharedPostgres.container

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("app.ethereum.rpc-url") { "http://localhost:8545" }
            registry.add("app.coingecko.api-key") { "test-key" }
            registry.add("app.cors.allowed-origins") { "http://localhost:3000" }
        }
    }
}

private object SharedPostgres {
    val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("ledger_test")
        .withUsername("ledger")
        .withPassword("ledger")
        .also { it.start() }
}
