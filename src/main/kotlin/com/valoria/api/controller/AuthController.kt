package com.valoria.api.controller

import com.valoria.api.dto.LoginRequest
import com.valoria.api.dto.RegisterRequest
import com.valoria.api.security.JwtService
import com.valoria.api.security.TokenResponse
import com.valoria.api.service.AuthService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtService: JwtService,
) {
    @PostMapping("/guest")
    fun guest(): TokenResponse = jwtService.issueGuest()

    @PostMapping("/login")
    @PreAuthorize("hasAnyRole('GUEST', 'USER', 'ADMIN')")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse = authService.login(request)

    @PostMapping("/register")
    @PreAuthorize("hasRole('GUEST')")
    fun register(@Valid @RequestBody request: RegisterRequest): TokenResponse = authService.register(request)
}

