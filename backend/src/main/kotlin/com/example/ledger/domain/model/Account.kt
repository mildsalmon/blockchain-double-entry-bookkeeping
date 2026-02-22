package com.example.ledger.domain.model

import java.time.Instant

data class Account(
    val id: Long? = null,
    val code: String,
    val name: String,
    val category: AccountCategory,
    val system: Boolean,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    fun validateSystemUpdate(newCode: String): Unit {
        if (system && code != newCode) {
            throw IllegalArgumentException("System account code cannot be changed")
        }
    }
}
