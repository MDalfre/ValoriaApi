package com.valoria.api.repository

import com.valoria.api.dto.*
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import com.valoria.api.service.ValoriaThroneStatusService
import java.util.concurrent.atomic.AtomicReference

@Repository
class OpenMuRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val passwordEncoder: PasswordEncoder,
    private val valoriaThroneStatusService: ValoriaThroneStatusService,
) {
    private val valoriaThroneStatusCache = AtomicReference<CachedValoriaThroneStatus?>(null)

    fun findAccountForLogin(loginName: String): LoginAccount? =
        jdbc.query(
            """SELECT "Id", "LoginName", "PasswordHash", "State" FROM data."Account"
               WHERE lower("LoginName") = lower(:loginName)""",
            mapOf("loginName" to loginName),
        ) { result, _ ->
            LoginAccount(
                result.getObject("Id", UUID::class.java),
                result.getString("LoginName"),
                result.getString("PasswordHash"),
                result.getInt("State"),
            )
        }.singleOrNull()

    @Transactional
    fun createAccount(request: RegisterRequest, trialDays: Int, trialLevel: Int): UUID {
        val accountId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val params = MapSqlParameterSource()
            .addValue("id", accountId)
            .addValue("loginName", request.loginName)
            .addValue("passwordHash", passwordEncoder.encode(request.password))
            .addValue("securityCode", request.securityCode)
            .addValue("email", request.email)
            .addValue("registrationDate", now)
        try {
            jdbc.update(
                """INSERT INTO data."Account"
                   ("Id", "LoginName", "PasswordHash", "SecurityCode", "EMail", "RegistrationDate",
                    "State", "TimeZone", "VaultPassword", "IsVaultExtended", "IsTemplate", "LanguageIsoCode", "IsBot")
                   VALUES (:id, :loginName, :passwordHash, :securityCode, :email, :registrationDate,
                    0, 0, '', false, false, 'pt', false)""",
                params,
            )
        } catch (exception: DuplicateKeyException) {
            throw AccountAlreadyExistsException()
        }

        if (trialDays > 0) {
            jdbc.update(
                """INSERT INTO data."AccountVipEntitlement"
                   ("Id", "AccountId", "VipLevel", "StartsAtUtc", "ExpiresAtUtc", "Source",
                    "SourceReference", "RevokedAtUtc", "CreatedAtUtc")
                   VALUES (:id, :accountId, :level, :startsAt, :expiresAt, 0,
                    :sourceReference, NULL, :createdAt)""",
                mapOf(
                    "id" to UUID.randomUUID(),
                    "accountId" to accountId,
                    "level" to trialLevel,
                    "startsAt" to now,
                    "expiresAt" to now.plusDays(trialDays.toLong()),
                    "sourceReference" to "website-account-creation:$accountId",
                    "createdAt" to now,
                ),
            )
        }
        return accountId
    }

    fun accountSummary(accountId: UUID): AccountSummary? =
        jdbc.query(
            """SELECT a."Id", a."LoginName", a."EMail", a."RegistrationDate",
                      COALESCE(v."VipLevel", 0) AS vip_level, v."ExpiresAtUtc"
               FROM data."Account" a
               LEFT JOIN LATERAL (
                   SELECT e."VipLevel", e."ExpiresAtUtc"
                   FROM data."AccountVipEntitlement" e
                   WHERE e."AccountId" = a."Id"
                     AND e."RevokedAtUtc" IS NULL
                     AND e."StartsAtUtc" <= now()
                     AND (e."ExpiresAtUtc" IS NULL OR e."ExpiresAtUtc" > now())
                   ORDER BY e."VipLevel" DESC, e."ExpiresAtUtc" DESC NULLS FIRST
                   LIMIT 1
               ) v ON true
               WHERE a."Id" = :accountId""",
            mapOf("accountId" to accountId),
        ) { result, _ ->
            val expiration = result.getTimestamp("ExpiresAtUtc")?.toInstant()
            val level = result.getInt("vip_level")
            AccountSummary(
                result.getObject("Id", UUID::class.java),
                result.getString("LoginName"),
                result.getString("EMail"),
                result.getTimestamp("RegistrationDate").toInstant(),
                VipSummary(level > 0, level, expiration),
            )
        }.singleOrNull()

    fun characters(accountId: UUID): List<CharacterSummary> =
        jdbc.query(characterQuery + """ WHERE c."AccountId" = :accountId ORDER BY c."CharacterSlot" """, mapOf("accountId" to accountId)) {
                result, _ -> mapCharacter(result)
        }

    fun topLevel(limit: Int): List<PlayerRankingEntry> =
        ranking(levelOrder, limit)

    fun topPk(limit: Int): List<PlayerRankingEntry> =
        ranking("""c."State" DESC, c."PlayerKillCount" DESC, level DESC, c."Name" """, limit)

    fun topGuilds(limit: Int): List<GuildRankingEntry> =
        jdbc.query(
            """SELECT g."Name", g."Score", count(gm."Id") AS members
               FROM guild."Guild" g
               LEFT JOIN guild."GuildMember" gm ON gm."GuildId" = g."Id"
               GROUP BY g."Id"
               ORDER BY g."Score" DESC, members DESC, g."Name"
               LIMIT :limit""",
            mapOf("limit" to limit),
        ) { result, row -> GuildRankingEntry(row + 1, result.getString("Name"), result.getInt("Score"), result.getInt("members")) }

    fun serverRates(): List<ServerRate> =
        jdbc.query(
            """SELECT s."ServerID", s."Description", s."ExperienceRate" AS server_rate, s."PvpEnabled",
                      g."ExperienceRate" AS game_rate, g."MasterExperienceRate" AS master_rate
               FROM config."GameServerDefinition" s
               JOIN config."GameConfiguration" g ON g."Id" = s."GameConfigurationId"
               ORDER BY s."ServerID" """,
            emptyMap<String, Any>(),
        ) { result, _ ->
            val serverRate = result.getDouble("server_rate")
            ServerRate(
                result.getInt("ServerID"),
                result.getString("Description"),
                serverRate * result.getDouble("game_rate"),
                serverRate * result.getDouble("master_rate"),
                result.getBoolean("PvpEnabled"),
            )
        }

    fun valoriaThroneStatus(): ValoriaThroneStatus {
        val now = Instant.now()
        val cached = valoriaThroneStatusCache.get()
        if (cached != null && cached.expiresAt.isAfter(now)) return cached.status

        synchronized(valoriaThroneStatusCache) {
            val current = valoriaThroneStatusCache.get()
            if (current != null && current.expiresAt.isAfter(now)) return current.status
            val status = loadValoriaThroneStatus(now)
            valoriaThroneStatusCache.set(CachedValoriaThroneStatus(status, now.plusSeconds(30)))
            return status
        }
    }

    private fun loadValoriaThroneStatus(now: Instant): ValoriaThroneStatus {
        val configurations = jdbc.query(
            """SELECT "CustomConfiguration"
               FROM config."PlugInConfiguration"
               WHERE "TypeId" = CAST(:typeId AS uuid)""",
            mapOf("typeId" to VALORIA_THRONE_PLUGIN_TYPE_ID),
        ) { result, _ -> result.getString("CustomConfiguration") }
        val status = valoriaThroneStatusService.fromConfigurations(configurations, now)
        val guild = status.guild ?: return status
        val logo = jdbc.query(
            """SELECT "Logo"
               FROM guild."Guild"
               WHERE lower("Name") = lower(:name)
               ORDER BY "Id"
               LIMIT 1""",
            mapOf("name" to guild.name),
        ) { result, _ -> result.getBytes("Logo") }.singleOrNull()
        return status.copy(guild = guild.copy(emblem = valoriaThroneStatusService.guildEmblem(logo)))
    }

    private fun ranking(order: String, limit: Int): List<PlayerRankingEntry> =
        jdbc.query(
            """$characterQuery
               JOIN data."Account" a ON a."Id" = c."AccountId"
               WHERE a."State" NOT IN (2, 3)
                 AND c."CharacterStatus" <> 32
               ORDER BY $order
               LIMIT :limit""",
            mapOf("limit" to limit),
        ) { result, row ->
            val character = mapCharacter(result)
            PlayerRankingEntry(
                row + 1,
                character.name,
                character.characterClass,
                character.level,
                character.resets,
                character.playerKillCount,
                character.heroState,
            )
        }

    private fun mapCharacter(result: ResultSet) = CharacterSummary(
        result.getObject("Id", UUID::class.java),
        result.getString("Name"),
        result.getString("class_name"),
        result.getInt("level"),
        result.getInt("resets"),
        result.getInt("PlayerKillCount"),
        result.getInt("State"),
    )

    companion object {
        // MUnique.OpenMU.PlugIns.ValoriaThrone.ValoriaThronePlugIn [Guid].
        private const val VALORIA_THRONE_PLUGIN_TYPE_ID = "d066fcd8-6b7e-4a6e-8d77-f7bfb4096c1e"
        private const val levelOrder = """resets DESC, level DESC, c."Experience" DESC, c."Name" """
        private const val characterQuery =
            """SELECT c."Id", c."Name", cc."Name" AS class_name, c."PlayerKillCount", c."State",
                      COALESCE(stats.level, 1)::int AS level,
                      COALESCE(stats.resets, 0)::int AS resets
               FROM data."Character" c
               JOIN config."CharacterClass" cc ON cc."Id" = c."CharacterClassId"
               LEFT JOIN LATERAL (
                   SELECT MAX(sa."Value") FILTER (WHERE ad."Designation" = 'Level') AS level,
                          MAX(sa."Value") FILTER (WHERE ad."Designation" = 'Resets') AS resets
                   FROM data."StatAttribute" sa
                   JOIN config."AttributeDefinition" ad ON ad."Id" = sa."DefinitionId"
                   WHERE sa."CharacterId" = c."Id"
               ) stats ON true
            """
    }

    private data class CachedValoriaThroneStatus(val status: ValoriaThroneStatus, val expiresAt: Instant)
}

data class LoginAccount(
    val id: UUID,
    val loginName: String,
    val passwordHash: String,
    val state: Int,
) {
    val isAdmin: Boolean get() = state == 2 || state == 3
    val loginAllowed: Boolean get() = state != 4 && state != 5
}

class AccountAlreadyExistsException : RuntimeException()
