package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.AccountEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface SpringDataAccountRepository : JpaRepository<AccountEntity, Long> {
    fun findByCode(code: String): AccountEntity?

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO accounts (code, name, category, is_system, created_at, updated_at)
            VALUES (:code, :name, :category, :isSystem, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (code) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("code") code: String,
        @Param("name") name: String,
        @Param("category") category: String,
        @Param("isSystem") isSystem: Boolean
    ): Int
}
