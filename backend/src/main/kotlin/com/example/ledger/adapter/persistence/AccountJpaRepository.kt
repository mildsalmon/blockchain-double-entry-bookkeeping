package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.spring.SpringDataAccountRepository
import com.example.ledger.adapter.persistence.spring.SpringDataJournalLineRepository
import com.example.ledger.domain.model.Account
import com.example.ledger.domain.port.AccountRepository
import org.springframework.stereotype.Repository

@Repository
class AccountJpaRepository(
    private val springDataAccountRepository: SpringDataAccountRepository,
    private val springDataJournalLineRepository: SpringDataJournalLineRepository
) : AccountRepository {
    override fun save(account: Account): Account {
        return springDataAccountRepository.save(account.toEntity()).toDomain()
    }

    override fun findAll(): List<Account> {
        return springDataAccountRepository.findAll().map { it.toDomain() }
    }

    override fun findById(id: Long): Account? {
        return springDataAccountRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByCode(code: String): Account? {
        return springDataAccountRepository.findByCode(code)?.toDomain()
    }

    override fun deleteById(id: Long) {
        springDataAccountRepository.deleteById(id)
    }

    override fun isReferencedByJournalLines(code: String): Boolean {
        return springDataJournalLineRepository.existsByAccountCode(code)
    }
}
