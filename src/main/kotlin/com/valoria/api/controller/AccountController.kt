package com.valoria.api.controller

import com.valoria.api.dto.AccountSummary
import com.valoria.api.dto.CharacterSummary
import com.valoria.api.repository.OpenMuRepository
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/account")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
class AccountController(private val repository: OpenMuRepository) {
    @GetMapping
    fun account(@AuthenticationPrincipal jwt: Jwt): AccountSummary =
        repository.accountSummary(UUID.fromString(jwt.subject))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    @GetMapping("/characters")
    fun characters(@AuthenticationPrincipal jwt: Jwt): List<CharacterSummary> =
        repository.characters(UUID.fromString(jwt.subject))
}

