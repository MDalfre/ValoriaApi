package com.valoria.api.repository

import com.valoria.api.dto.AdminVipAccount
import com.valoria.api.dto.AdminVipAccountDetails
import com.valoria.api.dto.AdminVipStatus
import com.valoria.api.dto.GrantVipRequest
import com.valoria.api.dto.VipEntitlementDto
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class VipAdminRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun searchAccounts(query: String?, limit: Int): List<AdminVipAccount> {
        val normalizedQuery = query?.trim()?.lowercase().orEmpty()
        return jdbc.query(
            """$accountSelect
               WHERE (:query = '' OR lower(a."LoginName") LIKE :pattern OR lower(a."EMail") LIKE :pattern)
               ORDER BY a."LoginName"
               LIMIT :limit""",
            mapOf(
                "query" to normalizedQuery,
                "pattern" to "%$normalizedQuery%",
                "limit" to limit.coerceIn(1, 100),
                "isVipDefinitionId" to isVipDefinitionId,
            ),
        ) { result, _ -> mapAccount(result) }
    }

    fun accountDetails(accountId: UUID): AdminVipAccountDetails? {
        val account = jdbc.query(
            """$accountSelect WHERE a."Id" = :accountId""",
            mapOf(
                "accountId" to accountId,
                "isVipDefinitionId" to isVipDefinitionId,
            ),
        ) { result, _ -> mapAccount(result) }.singleOrNull() ?: return null
        return AdminVipAccountDetails(account, entitlements(accountId))
    }

    @Transactional
    fun grant(accountId: UUID, request: GrantVipRequest): AdminVipAccountDetails {
        require(accountExists(accountId)) { "Account not found." }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val sourceReference = request.sourceReference?.trim()?.takeIf(String::isNotEmpty)
        val existingEntitlement = sourceReference?.let(this::entitlementBySourceReference)
        if (existingEntitlement != null && existingEntitlement.accountId != accountId) {
            throw IllegalArgumentException("Source reference is already assigned to another account.")
        }
        if (sourceReference == null || existingEntitlement == null) {
            insertEntitlement(accountId, request, sourceReference, now)
        }
        return requireNotNull(accountDetails(accountId))
    }

    @Transactional
    fun revoke(entitlementId: UUID): AdminVipAccountDetails {
        val accountId = jdbc.query(
            """UPDATE data."AccountVipEntitlement"
               SET "RevokedAtUtc" = COALESCE("RevokedAtUtc", :revokedAt)
               WHERE "Id" = :entitlementId
               RETURNING "AccountId" """,
            mapOf(
                "entitlementId" to entitlementId,
                "revokedAt" to OffsetDateTime.now(ZoneOffset.UTC),
            ),
        ) { result, _ -> result.getObject("AccountId", UUID::class.java) }.singleOrNull()
            ?: throw IllegalArgumentException("VIP entitlement not found.")
        return requireNotNull(accountDetails(accountId))
    }

    private fun insertEntitlement(
        accountId: UUID,
        request: GrantVipRequest,
        sourceReference: String?,
        now: OffsetDateTime,
    ) {
        val expiration = request.days?.let {
            val latestExpiration = jdbc.queryForObject(
                """SELECT COALESCE(MAX("ExpiresAtUtc"), :now)
                   FROM data."AccountVipEntitlement"
                   WHERE "AccountId" = :accountId
                     AND "RevokedAtUtc" IS NULL
                     AND "ExpiresAtUtc" > :now""",
                mapOf("accountId" to accountId, "now" to now),
                OffsetDateTime::class.java,
            ) ?: now
            latestExpiration.plusDays(it.toLong())
        }
        try {
            jdbc.update(
                """INSERT INTO data."AccountVipEntitlement"
                   ("Id", "AccountId", "VipLevel", "StartsAtUtc", "ExpiresAtUtc", "Source",
                    "SourceReference", "RevokedAtUtc", "CreatedAtUtc")
                   VALUES (:id, :accountId, :vipLevel, :startsAt, :expiresAt, :source,
                    :sourceReference, NULL, :createdAt)""",
                mapOf(
                    "id" to UUID.randomUUID(),
                    "accountId" to accountId,
                    "vipLevel" to request.vipLevel,
                    "startsAt" to now,
                    "expiresAt" to expiration,
                    "source" to manualSource,
                    "sourceReference" to sourceReference,
                    "createdAt" to now,
                ),
            )
        } catch (exception: DuplicateKeyException) {
            if (sourceReference == null || entitlementBySourceReference(sourceReference) == null) {
                throw exception
            }
        }
    }

    private fun accountExists(accountId: UUID): Boolean =
        jdbc.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM data."Account" WHERE "Id" = :accountId)""",
            mapOf("accountId" to accountId),
            Boolean::class.java,
        ) == true

    private fun entitlements(accountId: UUID): List<VipEntitlementDto> =
        jdbc.query(
            """SELECT "Id", "VipLevel", "StartsAtUtc", "ExpiresAtUtc", "Source",
                      "SourceReference", "RevokedAtUtc", "CreatedAtUtc"
               FROM data."AccountVipEntitlement"
               WHERE "AccountId" = :accountId
               ORDER BY "CreatedAtUtc" DESC""",
            mapOf("accountId" to accountId),
        ) { result, _ -> mapEntitlement(result) }

    private fun entitlementBySourceReference(sourceReference: String): ExistingEntitlement? =
        jdbc.query(
            """SELECT "AccountId" FROM data."AccountVipEntitlement"
               WHERE "SourceReference" = :sourceReference""",
            mapOf("sourceReference" to sourceReference),
        ) { result, _ ->
            ExistingEntitlement(
                result.getObject("AccountId", UUID::class.java),
            )
        }.singleOrNull()

    private fun mapAccount(result: ResultSet): AdminVipAccount {
        val entitlementLevel = result.getInt("entitlement_level")
        val legacyLevel = result.getInt("legacy_level")
        val usesLegacy = legacyLevel >= entitlementLevel && legacyLevel > 0
        val status = if (usesLegacy) {
            AdminVipStatus(true, legacyLevel, null, "LEGACY", true)
        } else {
            AdminVipStatus(
                entitlementLevel > 0,
                entitlementLevel,
                result.getTimestamp("vip_expiration")?.toInstant(),
                result.getObject("vip_source")?.let { sourceName((it as Number).toInt()) },
                false,
            )
        }
        return AdminVipAccount(
            result.getObject("Id", UUID::class.java),
            result.getString("LoginName"),
            result.getString("EMail"),
            result.getInt("State"),
            result.getTimestamp("RegistrationDate").toInstant(),
            status,
        )
    }

    private fun mapEntitlement(result: ResultSet): VipEntitlementDto {
        val startsAt = result.getTimestamp("StartsAtUtc").toInstant()
        val expiresAt = result.getTimestamp("ExpiresAtUtc")?.toInstant()
        val revokedAt = result.getTimestamp("RevokedAtUtc")?.toInstant()
        val now = java.time.Instant.now()
        return VipEntitlementDto(
            result.getObject("Id", UUID::class.java),
            result.getInt("VipLevel"),
            startsAt,
            expiresAt,
            sourceName(result.getInt("Source")),
            result.getString("SourceReference"),
            revokedAt,
            result.getTimestamp("CreatedAtUtc").toInstant(),
            revokedAt == null && startsAt <= now && (expiresAt == null || expiresAt > now),
        )
    }

    companion object {
        private val isVipDefinitionId = UUID.fromString("195474D6-59A2-4033-9C30-8628ECC0097E")
        private const val manualSource = 2
        private const val accountSelect =
            """SELECT a."Id", a."LoginName", a."EMail", a."State", a."RegistrationDate",
                      COALESCE(v."VipLevel", 0) AS entitlement_level,
                      v."ExpiresAtUtc" AS vip_expiration,
                      v."Source" AS vip_source,
                      COALESCE(legacy.vip_level, 0) AS legacy_level
               FROM data."Account" a
               LEFT JOIN LATERAL (
                   SELECT e."VipLevel", e."ExpiresAtUtc", e."Source"
                   FROM data."AccountVipEntitlement" e
                   WHERE e."AccountId" = a."Id"
                     AND e."RevokedAtUtc" IS NULL
                     AND e."StartsAtUtc" <= now()
                     AND (e."ExpiresAtUtc" IS NULL OR e."ExpiresAtUtc" > now())
                   ORDER BY e."VipLevel" DESC, e."ExpiresAtUtc" DESC NULLS FIRST
                   LIMIT 1
               ) v ON true
               LEFT JOIN LATERAL (
                   SELECT MAX(sa."Value")::int AS vip_level
                   FROM data."StatAttribute" sa
                   WHERE sa."AccountId" = a."Id"
                     AND sa."DefinitionId" = :isVipDefinitionId
               ) legacy ON true"""

        private fun sourceName(source: Int): String =
            when (source) {
                0 -> "TRIAL"
                1 -> "PURCHASE"
                2 -> "MANUAL"
                3 -> "COMPENSATION"
                else -> "UNKNOWN"
            }
    }

    private data class ExistingEntitlement(
        val accountId: UUID,
    )
}
