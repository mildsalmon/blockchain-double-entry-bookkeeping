package com.example.ledger.adapter.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
class AdminCorrectionSecurityConfig(
    private val credentialStore: AdminCorrectionCredentialStore
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        return UserDetailsService { username ->
            val credential = try {
                credentialStore.findByUsername(username)
            } catch (_: IllegalArgumentException) {
                null
            } ?: throw UsernameNotFoundException("Admin correction user not found")

            User.withUsername(credential.username)
                .password(
                    if (credential.passwordEncoded) credential.password
                    else passwordEncoder.encode(credential.password)
                )
                .roles(ADMIN_CORRECTION_ROLE)
                .build()
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic(Customizer.withDefaults())
            .authorizeHttpRequests {
                it.requestMatchers("/api/admin-corrections/**", "/api/wallets/*/admin-corrections/**")
                    .hasRole(ADMIN_CORRECTION_ROLE)
                it.anyRequest().permitAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(::writeUnauthorizedResponse)
            }

        return http.build()
    }

    private fun writeUnauthorizedResponse(
        request: HttpServletRequest,
        response: HttpServletResponse,
        ex: org.springframework.security.core.AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"message":"Admin correction requires authenticated admin credentials"}""")
    }

    private companion object {
        const val ADMIN_CORRECTION_ROLE = "ADMIN_CORRECTION"
    }
}
