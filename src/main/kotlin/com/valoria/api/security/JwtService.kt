package com.valoria.api.security

import com.valoria.api.config.AppProperties
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class JwtService(
    private val encoder: JwtEncoder,
    private val properties: AppProperties,
) {
    fun issueGuest(): TokenResponse = issue("guest:${UUID.randomUUID()}", listOf("GUEST"), null)

    fun issueAccount(accountId: UUID, loginName: String, admin: Boolean): TokenResponse {
        val roles = if (admin) listOf("USER", "ADMIN") else listOf("USER")
        return issue(accountId.toString(), roles, loginName)
    }

    private fun issue(subject: String, roles: List<String>, loginName: String?): TokenResponse {
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(properties.jwt.accessMinutes, ChronoUnit.MINUTES)
        val claims = JwtClaimsSet.builder()
            .issuer(properties.jwt.issuer)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(subject)
            .claim(ROLES_CLAIM, roles)
            .apply { loginName?.let { claim("login", it) } }
            .build()
        return TokenResponse(encoder.encode(JwtEncoderParameters.from(claims)).tokenValue, expiresAt)
    }

    companion object {
        const val ROLES_CLAIM = "roles"
    }
}

data class TokenResponse(
    val accessToken: String,
    val expiresAt: Instant,
    val tokenType: String = "Bearer",
)

