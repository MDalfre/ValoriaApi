package com.valoria.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ValoriaThroneStatusServiceTest {
    private val now = Instant.parse("2026-07-31T18:00:00Z")
    private val service = ValoriaThroneStatusService(ObjectMapper())

    @Test fun `returns the newest active reign and configured era benefit`() {
        val status = service.fromConfigurations(listOf(config("Luck", now.minusSeconds(60)), config("Ascension", now.minusSeconds(120))), now)
        assertThat(status.hasActiveReign).isTrue()
        assertThat(status.era?.code).isEqualTo("Luck")
        assertThat(status.remainingSeconds).isEqualTo(3600)
    }

    @Test fun `returns the neutral era when it has not been proclaimed`() {
        val status = service.fromConfigurations(listOf(config("None", now.minusSeconds(60))), now)
        assertThat(status.era?.name).isEqualTo("Era ainda não proclamada")
    }

    @Test fun `ignores absent invalid and expired configurations`() {
        val status = service.fromConfigurations(listOf(null, "not json", config("Luck", now.minusSeconds(7200))), now)
        assertThat(status.hasActiveReign).isFalse()
        assertThat(status.remainingSeconds).isNull()
    }

    @Test fun `uses the neutral fallback for an unknown era`() {
        val status = service.fromConfigurations(listOf(config("Uncharted", now.minusSeconds(60))), now)
        assertThat(status.era?.code).isEqualTo("None")
    }

    @Test fun `converts a valid OpenMU guild bitmap to an SVG data URI`() {
        val emblem = service.guildEmblem(ByteArray(32) { 0x1f.toByte() })
        assertThat(emblem).startsWith("data:image/svg+xml;base64,")
        assertThat(service.guildEmblem(ByteArray(31))).isNull()
    }

    private fun config(era: String, startedAt: Instant): String {
        val id = UUID.nameUUIDFromBytes(startedAt.toString().toByteArray())
        return """{"ImperialReign":{"Id":"$id","EmperorCharacterId":"${UUID.randomUUID()}","EmperorCharacterName":"Manaowar","ImperialGuildId":123,"ImperialGuildName":"Valoria","StartedAt":"$startedAt","ExpiresAt":"${startedAt.plusSeconds(3660)}","SelectedEra":"$era"},"ImperialEras":{"LuckChaosMachineMultiplier":1.2,"LuckJewelMultiplier":1.1}}"""
    }
}
