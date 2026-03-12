package com.example.ledger.application.dto

import com.example.ledger.domain.model.AccountCategory
import jakarta.validation.constraints.NotBlank

data class AccountCreateRequest(
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    val category: AccountCategory
)

data class AccountUpdateRequest(
    val code: String? = null,
    val name: String? = null,
    val category: AccountCategory? = null
)

data class AccountResponse(
    val id: Long?,
    val code: String,
    val name: String,
    val category: AccountCategory,
    val system: Boolean
)
