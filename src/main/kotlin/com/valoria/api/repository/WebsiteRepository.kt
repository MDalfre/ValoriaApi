package com.valoria.api.repository

import com.valoria.api.dto.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class WebsiteRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun notices(limit: Int): List<NoticeDto> =
        jdbc.query(
            """SELECT id, title, body, published_at FROM website.notice
               WHERE active = true AND published_at <= now()
               ORDER BY published_at DESC LIMIT :limit""",
            mapOf("limit" to limit),
        ) { result, _ ->
            NoticeDto(
                result.getObject("id", UUID::class.java),
                result.getString("title"),
                result.getString("body"),
                result.getTimestamp("published_at").toInstant(),
            )
        }

    fun createNotice(request: CreateNoticeRequest, actorId: UUID): NoticeDto {
        val notice = NoticeDto(UUID.randomUUID(), request.title.trim(), request.body.trim(), Instant.now())
        jdbc.update(
            """INSERT INTO website.notice (id, title, body, published_at, created_by)
               VALUES (:id, :title, :body, :publishedAt, :createdBy)""",
            mapOf(
                "id" to notice.id,
                "title" to notice.title,
                "body" to notice.body,
                "publishedAt" to OffsetDateTime.ofInstant(notice.publishedAt, ZoneOffset.UTC),
                "createdBy" to actorId,
            ),
        )
        return notice
    }

    fun schedules(): List<MiniGameScheduleDto> =
        jdbc.query(
            """SELECT id, name, schedule_text FROM website.mini_game_schedule
               WHERE active = true ORDER BY sort_order, name""",
            emptyMap<String, Any>(),
        ) { result, _ ->
            MiniGameScheduleDto(
                result.getObject("id", UUID::class.java),
                result.getString("name"),
                result.getString("schedule_text"),
            )
        }

    fun downloads(): List<ClientDownloadDto> =
        jdbc.query(
            """SELECT id, label, url, version, file_size, checksum_sha256
               FROM website.client_download WHERE active = true ORDER BY sort_order, label""",
            emptyMap<String, Any>(),
        ) { result, _ ->
            ClientDownloadDto(
                result.getObject("id", UUID::class.java),
                result.getString("label"),
                result.getString("url"),
                result.getString("version"),
                result.getString("file_size"),
                result.getString("checksum_sha256"),
            )
        }

    fun audit(actorId: UUID?, action: String, target: String?, details: String?, remoteAddress: String?) {
        jdbc.update(
            """INSERT INTO website.audit_log
               (id, actor_account_id, action, target, details, remote_address, created_at)
               VALUES (:id, :actor, :action, :target, :details, :remote, :createdAt)""",
            mapOf(
                "id" to UUID.randomUUID(),
                "actor" to actorId,
                "action" to action,
                "target" to target,
                "details" to details,
                "remote" to remoteAddress,
                "createdAt" to OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
    }
}
