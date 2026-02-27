package com.example.ledger.integration

import com.example.ledger.domain.model.AccountCategory
import com.example.ledger.domain.port.AccountRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountRaceIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Test
    fun `concurrent ensure account insert keeps exactly one row`() {
        val code = "자산:암호화폐:ERC20:RACE"
        val threadCount = 8
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errors = mutableListOf<Throwable>()

        val executor = Executors.newFixedThreadPool(threadCount)
        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await(3, TimeUnit.SECONDS)
                    accountRepository.insertIfAbsent(
                        code = code,
                        name = "RACE token asset",
                        category = AccountCategory.ASSET,
                        system = true
                    )
                } catch (t: Throwable) {
                    synchronized(errors) { errors.add(t) }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertEquals(emptyList(), errors, "Expected no errors but got: $errors")

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE code = ?",
            Long::class.java,
            code
        )
        assertEquals(1L, count)
    }
}
