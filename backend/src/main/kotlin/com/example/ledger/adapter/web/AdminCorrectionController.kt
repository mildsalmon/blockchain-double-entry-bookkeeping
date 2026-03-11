package com.example.ledger.adapter.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/admin-corrections")
class AdminCorrectionController {

    @GetMapping("/session")
    fun getSession(principal: Principal): AdminCorrectionSessionResponse {
        return AdminCorrectionSessionResponse(
            authenticated = true,
            username = principal.name
        )
    }
}

data class AdminCorrectionSessionResponse(
    val authenticated: Boolean,
    val username: String
)
