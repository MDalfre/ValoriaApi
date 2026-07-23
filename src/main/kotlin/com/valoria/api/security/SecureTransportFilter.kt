package com.valoria.api.security

import com.valoria.api.config.AppProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class SecureTransportFilter(
    private val properties: AppProperties,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !properties.requireHttps || request.requestURI !in protectedPaths
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val forwardedProtocol = request.getHeader("X-Forwarded-Proto")
        if (!request.isSecure && !forwardedProtocol.equals("https", ignoreCase = true)) {
            response.sendError(HttpStatus.UPGRADE_REQUIRED.value(), "HTTPS is required for authentication.")
            return
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        private val protectedPaths = setOf("/api/auth/login", "/api/auth/register")
    }
}

