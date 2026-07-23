package com.valoria.api.service

import com.valoria.api.config.AppProperties
import com.valoria.api.dto.LoginRequest
import com.valoria.api.dto.RegisterRequest
import com.valoria.api.repository.OpenMuRepository
import com.valoria.api.security.JwtService
import com.valoria.api.security.TokenResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val repository: OpenMuRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val properties: AppProperties,
) {
    fun login(request: LoginRequest): TokenResponse {
        val account = repository.findAccountForLogin(request.loginName)
            ?: throw BadCredentialsException("Invalid credentials")
        if (!account.loginAllowed || !passwordEncoder.matches(request.password, account.passwordHash)) {
            throw BadCredentialsException("Invalid credentials")
        }
        return jwtService.issueAccount(account.id, account.loginName, account.isAdmin)
    }

    fun register(request: RegisterRequest): TokenResponse {
        val accountId = repository.createAccount(
            request,
            properties.registration.trialVipDays.coerceIn(0, 3650),
            properties.registration.trialVipLevel.coerceIn(1, 100),
        )
        return jwtService.issueAccount(accountId, request.loginName, false)
    }
}

