package com.valoria.api.controller

import com.valoria.api.dto.AdminVipAccount
import com.valoria.api.dto.AdminVipAccountDetails
import com.valoria.api.dto.GrantVipRequest
import com.valoria.api.repository.VipAdminRepository
import com.valoria.api.repository.WebsiteRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/vip")
@PreAuthorize("hasRole('ADMIN')")
class AdminVipController(
    private val vip: VipAdminRepository,
    private val website: WebsiteRepository,
) {
    @GetMapping("/accounts")
    fun accounts(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "25") limit: Int,
    ): List<AdminVipAccount> = vip.searchAccounts(query, limit)

    @GetMapping("/accounts/{accountId}")
    fun account(@PathVariable accountId: UUID): AdminVipAccountDetails =
        vip.accountDetails(accountId) ?: throw IllegalArgumentException("Account not found.")

    @PostMapping("/accounts/{accountId}/entitlements")
    fun grant(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable accountId: UUID,
        @Valid @RequestBody requestBody: GrantVipRequest,
        request: HttpServletRequest,
    ): AdminVipAccountDetails {
        val result = vip.grant(accountId, requestBody)
        website.audit(
            UUID.fromString(jwt.subject),
            "VIP_GRANT",
            accountId.toString(),
            "level=${requestBody.vipLevel},days=${requestBody.days ?: "permanent"}",
            request.remoteAddr,
        )
        return result
    }

    @PostMapping("/entitlements/{entitlementId}/revoke")
    fun revoke(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable entitlementId: UUID,
        request: HttpServletRequest,
    ): AdminVipAccountDetails {
        val result = vip.revoke(entitlementId)
        website.audit(
            UUID.fromString(jwt.subject),
            "VIP_REVOKE",
            entitlementId.toString(),
            null,
            request.remoteAddr,
        )
        return result
    }
}
