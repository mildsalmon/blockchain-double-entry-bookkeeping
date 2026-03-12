package com.example.ledger.adapter.web

import com.example.ledger.application.dto.AccountCreateRequest
import com.example.ledger.application.dto.AccountResponse
import com.example.ledger.application.dto.AccountUpdateRequest
import com.example.ledger.domain.model.Account
import com.example.ledger.domain.port.AccountRepository
import com.example.ledger.domain.service.AuditService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountRepository: AccountRepository,
    private val auditService: AuditService
) {
    @GetMapping
    fun getAccounts(): List<AccountResponse> {
        return accountRepository.findAll().map { it.toResponse() }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(@Valid @RequestBody request: AccountCreateRequest): AccountResponse {
        val saved = accountRepository.save(
            Account(
                code = request.code,
                name = request.name,
                category = request.category,
                system = false,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        auditService.log("ACCOUNT", saved.id.toString(), "CREATE", newValue = mapOf("code" to saved.code, "name" to saved.name))
        return saved.toResponse()
    }

    @PatchMapping("/{id}")
    fun updateAccount(@PathVariable id: Long, @RequestBody request: AccountUpdateRequest): AccountResponse {
        val existing = accountRepository.findById(id)
            ?: throw IllegalArgumentException("Account not found: $id")

        val newCode = request.code ?: existing.code
        existing.validateSystemUpdate(newCode)

        val updated = existing.copy(
            code = newCode,
            name = request.name ?: existing.name,
            category = request.category ?: existing.category,
            updatedAt = Instant.now()
        )

        val saved = accountRepository.save(updated)
        auditService.log(
            "ACCOUNT",
            id.toString(),
            "UPDATE",
            oldValue = mapOf("code" to existing.code, "name" to existing.name),
            newValue = mapOf("code" to saved.code, "name" to saved.name)
        )

        return saved.toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(@PathVariable id: Long) {
        val existing = accountRepository.findById(id)
            ?: throw IllegalArgumentException("Account not found: $id")

        if (existing.system) {
            throw IllegalArgumentException("System account cannot be deleted")
        }

        if (accountRepository.isReferencedByJournalLines(existing.code)) {
            throw IllegalArgumentException("Referenced account cannot be deleted")
        }

        accountRepository.deleteById(id)
        auditService.log("ACCOUNT", id.toString(), "DELETE", oldValue = mapOf("code" to existing.code, "name" to existing.name))
    }

    private fun Account.toResponse(): AccountResponse {
        return AccountResponse(
            id = id,
            code = code,
            name = name,
            category = category,
            system = system
        )
    }
}
