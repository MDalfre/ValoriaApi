package com.valoria.api.controller

import com.valoria.api.dto.*
import com.valoria.api.repository.WebsiteRepository
import com.valoria.api.service.BackupService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val website: WebsiteRepository,
    private val backups: BackupService,
) {
    @PostMapping("/notices")
    fun createNotice(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateNoticeRequest,
    ): NoticeDto = website.createNotice(request, UUID.fromString(jwt.subject))

    @GetMapping("/backups")
    fun latestBackups(): List<BackupDto> = backups.latest()

    @GetMapping("/backups/{fileName:.+}")
    fun download(@PathVariable fileName: String): ResponseEntity<Resource> {
        val resource = backups.resource(fileName)
        val disposition = ContentDisposition.attachment()
            .filename(fileName, StandardCharsets.UTF_8)
            .build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(resource)
    }

    @PostMapping("/backups/upload")
    fun upload(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestPart("file") file: MultipartFile,
        request: HttpServletRequest,
    ): BackupDto {
        val result = backups.upload(file)
        website.audit(UUID.fromString(jwt.subject), "BACKUP_UPLOAD", result.fileName, null, request.remoteAddr)
        return result
    }

    @PostMapping("/backups/{fileName:.+}/restore")
    fun restore(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable fileName: String,
        @Valid @RequestBody requestBody: RestoreRequest,
        request: HttpServletRequest,
    ): RestoreResult {
        website.audit(UUID.fromString(jwt.subject), "BACKUP_RESTORE_REQUEST", fileName, null, request.remoteAddr)
        val result = backups.restore(fileName, requestBody.confirmation)
        website.audit(UUID.fromString(jwt.subject), "BACKUP_RESTORE_RESULT", fileName, result.message, request.remoteAddr)
        return result
    }
}

