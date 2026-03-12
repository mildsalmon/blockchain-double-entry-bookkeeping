package com.example.ledger.adapter.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "accounts")
data class AccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "code", nullable = false, unique = true)
    val code: String,
    @Column(name = "name", nullable = false)
    val name: String,
    @Column(name = "category", nullable = false)
    val category: String,
    @Column(name = "is_system", nullable = false)
    val isSystem: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
