package com.valoria.api.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.valoria.api.dto.ValoriaThroneEmperor
import com.valoria.api.dto.ValoriaThroneEra
import com.valoria.api.dto.ValoriaThroneGuild
import com.valoria.api.dto.ValoriaThroneStatus
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.Base64

/** Maps the persisted OpenMU plug-in configuration into the deliberately small public contract. */
@Service
class ValoriaThroneStatusService(
    private val objectMapper: ObjectMapper,
) {
    fun fromConfigurations(configurations: List<String?>, now: Instant = Instant.now()): ValoriaThroneStatus {
        val reign = configurations.mapNotNull(::readReign).maxWithOrNull(compareBy<Reign> { it.startedAt }.thenBy { it.id })
            ?: return inactive()
        if (!reign.expiresAt.isAfter(now)) return inactive()

        val era = era(reign.eraCode, reign.eraOptions)
        return ValoriaThroneStatus(
            hasActiveReign = true,
            emperor = ValoriaThroneEmperor(reign.emperorId, reign.emperorName),
            guild = ValoriaThroneGuild(reign.guildId, reign.guildName),
            era = era,
            startedAt = reign.startedAt,
            expiresAt = reign.expiresAt,
            remainingSeconds = Duration.between(now, reign.expiresAt).seconds.coerceAtLeast(0),
        )
    }

    /**
     * OpenMU stores the guild emblem as 32 bytes: two palette-indexed pixels per byte.
     * Convert it once on the server so public clients can render it as a normal image.
     */
    fun guildEmblem(logo: ByteArray?): String? {
        if (logo?.size != 32) return null
        val pixels = buildString {
            logo.forEachIndexed { byteIndex, value ->
                val y = byteIndex / 4
                val x = (byteIndex % 4) * 2
                appendRect(x, y, value.toInt().ushr(4) and 0x0f)
                appendRect(x + 1, y, value.toInt() and 0x0f)
            }
        }
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 8 8\" shape-rendering=\"crispEdges\">$pixels</svg>"
        return "data:image/svg+xml;base64,${Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8))}"
    }

    private fun readReign(json: String?): Reign? = try {
        if (json.isNullOrBlank()) return null
        val options = objectMapper.readTree(json)
        val node = options.at("/ImperialReign").takeUnless { it.isMissingNode || it.isNull } ?: return null
        Reign(
            id = UUID.fromString(node.requiredText("Id")),
            emperorId = UUID.fromString(node.requiredText("EmperorCharacterId")),
            emperorName = node.requiredText("EmperorCharacterName"),
            guildId = node.requiredText("ImperialGuildId").toLong(),
            guildName = node.requiredText("ImperialGuildName"),
            startedAt = Instant.parse(node.requiredText("StartedAt")),
            expiresAt = Instant.parse(node.requiredText("ExpiresAt")),
            eraCode = node.path("SelectedEra").asText("None"),
            eraOptions = options.path("ImperialEras"),
        )
    } catch (_: Exception) {
        // A malformed or manually edited configuration must not make a public endpoint fail.
        null
    }

    private fun era(code: String, options: JsonNode): ValoriaThroneEra = when (code.lowercase()) {
        "ascension", "1" -> ValoriaThroneEra("Ascension", "Era da Ascensão", "Experiência global aumentada em ${bonus(options, "AscensionExperienceMultiplier")}.")
        "fortune", "2" -> ValoriaThroneEra("Fortune", "Era da Fortuna", "Taxa de drop aumentada em ${bonus(options, "FortuneDropMultiplier")}.")
        "freedom", "3" -> ValoriaThroneEra("Freedom", "Era da Liberdade", "Personagens PK podem utilizar teleportes normais entre mapas.")
        "luck", "4" -> ValoriaThroneEra("Luck", "Era da Sorte", "Chances da Chaos Machine aumentadas em ${bonus(options, "LuckChaosMachineMultiplier")} e das joias em ${bonus(options, "LuckJewelMultiplier")}.")
        else -> ValoriaThroneEra("None", "Era ainda não proclamada", "O imperador ainda não escolheu a Era do reinado.")
    }

    private fun bonus(options: JsonNode, property: String): String {
        val multiplier = options.path(property).asDouble(1.0)
        return "${((multiplier - 1.0) * 100).coerceAtLeast(0.0).toInt()}%"
    }

    private fun inactive() = ValoriaThroneStatus(false, null, null, null, null, null, null)

    private fun StringBuilder.appendRect(x: Int, y: Int, colorIndex: Int) {
        val color = guildColors[colorIndex] ?: return
        append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"1\" height=\"1\" fill=\"").append(color).append("\"/>")
    }

    private fun JsonNode.requiredText(property: String): String = path(property).asText().takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing $property")

    private data class Reign(
        val id: UUID,
        val emperorId: UUID,
        val emperorName: String,
        val guildId: Long,
        val guildName: String,
        val startedAt: Instant,
        val expiresAt: Instant,
        val eraCode: String,
        val eraOptions: JsonNode,
    )

    private companion object {
        // Index 0 is transparent. The protocol persists palette indices, not encoded image bytes.
        val guildColors = arrayOf(
            null, "#171313", "#5d2623", "#bd4739", "#db8b3e", "#e8ca82", "#3f7046", "#77a84f",
            "#326b93", "#65aec8", "#40447d", "#73529b", "#a85b9f", "#ce799a", "#8b8275", "#f1ece2",
        )
    }
}
