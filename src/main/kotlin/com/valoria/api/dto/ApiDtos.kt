package com.valoria.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LoginRequest(
    @field:NotBlank @field:Size(min = 3, max = 10) val loginName: String,
    @field:NotBlank @field:Size(min = 3, max = 72) val password: String,
)

data class RegisterRequest(
    @field:Pattern(regexp = "^[a-zA-Z0-9]{3,10}$") val loginName: String,
    @field:Size(min = 8, max = 20) val password: String,
    @field:Pattern(regexp = "^[0-9]{3,10}$") val securityCode: String,
    @field:Email @field:Size(max = 254) val email: String,
)

data class AccountSummary(
    val id: UUID,
    val loginName: String,
    val email: String,
    val registrationDate: Instant,
    val vip: VipSummary,
)

data class VipSummary(
    val active: Boolean,
    val level: Int,
    val expiresAt: Instant?,
)

data class CharacterSummary(
    val id: UUID,
    val name: String,
    val characterClass: String,
    val level: Int,
    val resets: Int,
    val playerKillCount: Int,
    val heroState: Int,
)

data class PlayerRankingEntry(
    val position: Int,
    val name: String,
    val characterClass: String,
    val level: Int,
    val resets: Int,
    val playerKillCount: Int,
    val heroState: Int,
)

data class GuildRankingEntry(
    val position: Int,
    val name: String,
    val score: Int,
    val members: Int,
)

data class ServerRate(
    val serverId: Int,
    val name: String,
    val experienceMultiplier: Double,
    val masterExperienceMultiplier: Double,
    val pvpEnabled: Boolean,
)

data class NoticeDto(
    val id: UUID,
    val title: String,
    val body: String,
    val publishedAt: Instant,
)

data class CreateNoticeRequest(
    @field:NotBlank @field:Size(max = 140) val title: String,
    @field:NotBlank @field:Size(max = 10_000) val body: String,
)

data class MiniGameScheduleDto(
    val id: UUID,
    val name: String,
    val scheduleText: String,
)

data class ClientDownloadDto(
    val id: UUID,
    val label: String,
    val url: String,
    val version: String,
    val fileSize: String?,
    val checksumSha256: String?,
)

data class BackupDto(
    val fileName: String,
    val size: Long,
    val modifiedAt: Instant,
)

data class RestoreRequest(
    @field:NotBlank val confirmation: String,
)

data class RestoreResult(
    val accepted: Boolean,
    val message: String,
)

