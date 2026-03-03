package com.example.ledger.adapter.persistence

import com.example.ledger.domain.model.JournalStatus
import com.example.ledger.domain.model.TokenBalancePosition
import com.example.ledger.domain.port.BalanceDashboardRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant

@Repository
class BalanceDashboardJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : BalanceDashboardRepository {

    override fun findBalancePositions(walletAddress: String?, status: JournalStatus?): List<TokenBalancePosition> {
        val params = MapSqlParameterSource()
        val where = mutableListOf<String>()

        if (!walletAddress.isNullOrBlank()) {
            where += "rt.wallet_address = :walletAddress"
            params.addValue("walletAddress", walletAddress)
        }
        if (status != null) {
            where += "je.status = :status"
            params.addValue("status", status.name)
        }

        val whereSql = if (where.isEmpty()) {
            ""
        } else {
            " AND ${where.joinToString(" AND ")}"
        }

        val sql =
            """
            SELECT
              rt.wallet_address,
              jl.account_code,
              COALESCE(NULLIF(jl.token_symbol, ''), 'UNKNOWN') AS token_symbol,
              SUM(
                CASE
                  WHEN jl.token_quantity IS NULL THEN 0
                  WHEN jl.token_quantity < 0 THEN jl.token_quantity
                  WHEN jl.debit_amount > 0 THEN ABS(jl.token_quantity)
                  WHEN jl.credit_amount > 0 THEN -ABS(jl.token_quantity)
                  ELSE jl.token_quantity
                END
              ) AS quantity,
              MAX(je.entry_date) AS last_entry_date
            FROM journal_lines jl
            JOIN journal_entries je ON je.id = jl.journal_entry_id
            JOIN raw_transactions rt ON rt.id = je.raw_transaction_id
            WHERE jl.token_quantity IS NOT NULL
              AND jl.account_code LIKE '자산:암호화폐:%'
              $whereSql
            GROUP BY rt.wallet_address, jl.account_code, COALESCE(NULLIF(jl.token_symbol, ''), 'UNKNOWN')
            HAVING SUM(
                CASE
                  WHEN jl.token_quantity IS NULL THEN 0
                  WHEN jl.token_quantity < 0 THEN jl.token_quantity
                  WHEN jl.debit_amount > 0 THEN ABS(jl.token_quantity)
                  WHEN jl.credit_amount > 0 THEN -ABS(jl.token_quantity)
                  ELSE jl.token_quantity
                END
            ) <> 0
            ORDER BY rt.wallet_address ASC, token_symbol ASC, jl.account_code ASC
            """.trimIndent()

        return jdbcTemplate.query(sql, params) { rs, _ ->
            TokenBalancePosition(
                walletAddress = rs.getString("wallet_address"),
                accountCode = rs.getString("account_code"),
                tokenSymbol = rs.getString("token_symbol"),
                quantity = rs.getBigDecimal("quantity") ?: BigDecimal.ZERO,
                lastEntryDate = (rs.getObject("last_entry_date") as Timestamp).toInstant()
            )
        }
    }
}
