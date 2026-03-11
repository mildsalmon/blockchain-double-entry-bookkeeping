package com.example.ledger.adapter.web

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminCorrectionCredentialStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private val passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Test
    fun `file based encoded credentials are loaded and re-read without restart`() {
        val credentialFile = tempDir.resolve("admin-correction-users.txt")
        Files.writeString(
            credentialFile,
            """
            # admin correction operators
            ops-kim:{noop}first-password
            """.trimIndent()
        )

        val credentialStore = AdminCorrectionCredentialStore("", credentialFile.toString())
        val userDetailsService = AdminCorrectionSecurityConfig(credentialStore).userDetailsService(passwordEncoder)

        val initialUser = userDetailsService.loadUserByUsername("ops-kim")
        assertTrue(passwordEncoder.matches("first-password", initialUser.password))

        Files.writeString(credentialFile, "ops-kim:{noop}rotated-password")

        val rotatedUser = userDetailsService.loadUserByUsername("ops-kim")
        assertTrue(passwordEncoder.matches("rotated-password", rotatedUser.password))
    }

    @Test
    fun `missing credential file disables admin correction with clear reason`() {
        val missingFile = tempDir.resolve("missing-users.txt")
        val credentialStore = AdminCorrectionCredentialStore("", missingFile.toString())

        val status = credentialStore.status()

        assertFalse(status.enabled)
        assertNotNull(status.unavailableReason)
        assertTrue(status.unavailableReason.contains("Credential file not found"))
    }

    @Test
    fun `inline users remain available for local development`() {
        val credentialStore = AdminCorrectionCredentialStore(
            "ops-kim:test-admin-password,ops-lee:test-admin-password-2",
            ""
        )

        val status = credentialStore.status()
        val credential = credentialStore.findByUsername("ops-lee")

        assertTrue(status.enabled)
        assertEquals("ops-lee", credential?.username)
        assertEquals("test-admin-password-2", credential?.password)
        assertFalse(credential?.passwordEncoded ?: true)
    }
}
