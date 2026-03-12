package com.example.ledger.domain.port

import com.example.ledger.domain.model.Account
import com.example.ledger.domain.model.AccountCategory

interface AccountRepository {
    fun save(account: Account): Account
    fun insertIfAbsent(code: String, name: String, category: AccountCategory, system: Boolean): Int
    fun findAll(): List<Account>
    fun findById(id: Long): Account?
    fun findByCode(code: String): Account?
    fun deleteById(id: Long)
    fun isReferencedByJournalLines(code: String): Boolean
}
