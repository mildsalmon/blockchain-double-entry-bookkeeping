package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.AccountEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataAccountRepository : JpaRepository<AccountEntity, Long> {
    fun findByCode(code: String): AccountEntity?
}
