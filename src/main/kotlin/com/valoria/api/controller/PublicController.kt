package com.valoria.api.controller

import com.valoria.api.dto.*
import com.valoria.api.repository.OpenMuRepository
import com.valoria.api.repository.WebsiteRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/public")
class PublicController(
    private val openMu: OpenMuRepository,
    private val website: WebsiteRepository,
) {
    @GetMapping("/rankings/level")
    fun level(@RequestParam(defaultValue = "10") limit: Int): List<PlayerRankingEntry> =
        openMu.topLevel(limit.coerceIn(1, 100))

    @GetMapping("/rankings/pk")
    fun pk(@RequestParam(defaultValue = "10") limit: Int): List<PlayerRankingEntry> =
        openMu.topPk(limit.coerceIn(1, 100))

    @GetMapping("/rankings/guilds")
    fun guilds(@RequestParam(defaultValue = "10") limit: Int): List<GuildRankingEntry> =
        openMu.topGuilds(limit.coerceIn(1, 100))

    @GetMapping("/server-rates")
    fun rates(): List<ServerRate> = openMu.serverRates()

    @GetMapping("/valoria-throne/status")
    fun valoriaThroneStatus(): ResponseEntity<ValoriaThroneStatus> =
        ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
            .body(openMu.valoriaThroneStatus())

    @GetMapping("/notices")
    fun notices(@RequestParam(defaultValue = "6") limit: Int): List<NoticeDto> =
        website.notices(limit.coerceIn(1, 30))

    @GetMapping("/mini-games")
    fun miniGames(): List<MiniGameScheduleDto> = website.schedules()

    @GetMapping("/downloads")
    fun downloads(): List<ClientDownloadDto> = website.downloads()
}
