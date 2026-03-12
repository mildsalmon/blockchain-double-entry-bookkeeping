package com.example.ledger.adapter.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Component
class AdminCorrectionCredentialStore(
    @Value("\${app.admin-correction.users:}") private val inlineUsers: String,
    @Value("\${app.admin-correction.users-file:}") private val usersFile: String
) {
    fun status(): AdminCorrectionCredentialStatus {
        if (!hasConfiguredSource()) {
            return AdminCorrectionCredentialStatus(
                enabled = false,
                unavailableReason = "Admin correction is disabled on this server. Configure ADMIN_CORRECTION_USERS_FILE or ADMIN_CORRECTION_USERS to enable it."
            )
        }

        return try {
            val credentials = loadCredentials()
            if (credentials.isEmpty()) {
                AdminCorrectionCredentialStatus(
                    enabled = false,
                    unavailableReason = "Admin correction credentials are configured but empty."
                )
            } else {
                AdminCorrectionCredentialStatus(
                    enabled = true,
                    unavailableReason = null
                )
            }
        } catch (error: IllegalArgumentException) {
            AdminCorrectionCredentialStatus(
                enabled = false,
                unavailableReason = "Admin correction credential config is invalid: ${error.message}"
            )
        }
    }

    fun findByUsername(username: String): AdminCorrectionCredential? {
        return loadCredentials().firstOrNull { it.username == username }
    }

    fun loadCredentials(): List<AdminCorrectionCredential> {
        val combined = buildList {
            if (inlineUsers.isNotBlank()) {
                addAll(parseInlineUsers(inlineUsers))
            }
            val usersFilePath = usersFilePath()
            if (usersFilePath != null) {
                addAll(parseUsersFile(usersFilePath))
            }
        }

        val duplicates = combined.groupBy { it.username }
            .filterValues { it.size > 1 }
            .keys
            .sorted()
        require(duplicates.isEmpty()) {
            "Duplicate admin correction usernames are not allowed: ${duplicates.joinToString(", ")}"
        }

        return combined.sortedBy { it.username }
    }

    private fun hasConfiguredSource(): Boolean {
        return inlineUsers.isNotBlank() || usersFilePath() != null
    }

    private fun usersFilePath(): Path? {
        val trimmed = usersFile.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return Paths.get(trimmed).toAbsolutePath().normalize()
    }

    private fun parseInlineUsers(rawUsers: String): List<AdminCorrectionCredential> {
        return rawUsers
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapIndexed { index, entry ->
                parseCredentialEntry(
                    entry = entry,
                    source = "app.admin-correction.users[$index]",
                    encodedPasswordsRequired = false
                )
            }
    }

    private fun parseUsersFile(path: Path): List<AdminCorrectionCredential> {
        require(Files.exists(path)) {
            "Credential file not found: $path"
        }
        require(Files.isRegularFile(path)) {
            "Credential file is not a regular file: $path"
        }

        val entries = Files.readAllLines(path, StandardCharsets.UTF_8)
            .mapIndexedNotNull { index, rawLine ->
                val line = rawLine.trim()
                if (line.isBlank() || line.startsWith("#")) {
                    null
                } else {
                    parseCredentialEntry(
                        entry = line,
                        source = "$path:${index + 1}",
                        encodedPasswordsRequired = true
                    )
                }
            }

        require(entries.isNotEmpty()) {
            "Credential file is empty: $path"
        }

        return entries
    }

    private fun parseCredentialEntry(
        entry: String,
        source: String,
        encodedPasswordsRequired: Boolean
    ): AdminCorrectionCredential {
        val parts = entry.split(':', limit = 2)
        require(parts.size == 2) {
            "Invalid admin correction credential entry at $source: expected username:password"
        }

        val username = parts[0].trim()
        val password = parts[1].trim()
        require(username.isNotBlank() && password.isNotBlank()) {
            "Invalid admin correction credential entry at $source: username and password are required"
        }
        if (encodedPasswordsRequired) {
            require(password.startsWith("{") && password.contains('}')) {
                "Credential file entries must use encoded password format like {bcrypt}... at $source"
            }
        }

        return AdminCorrectionCredential(
            username = username,
            password = password,
            passwordEncoded = encodedPasswordsRequired
        )
    }
}

data class AdminCorrectionCredential(
    val username: String,
    val password: String,
    val passwordEncoded: Boolean
)

data class AdminCorrectionCredentialStatus(
    val enabled: Boolean,
    val unavailableReason: String?
)
