package com.valoria.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties("app")
data class AppProperties(
    val frontendOrigin: String,
    val requireHttps: Boolean = false,
    val jwt: Jwt,
    val registration: Registration = Registration(),
    val backup: Backup = Backup(),
) {
    data class Jwt(
        val secretBase64: String,
        val issuer: String = "valoria-api",
        val accessMinutes: Long = 30,
    )

    data class Registration(
        val trialVipDays: Int = 15,
        val trialVipLevel: Int = 1,
    )

    data class Backup(
        val directory: Path = Path.of("/backups"),
        val stagingDirectory: Path = Path.of("/restore-staging"),
        val restoreEnabled: Boolean = false,
        val pgRestorePath: String = "pg_restore",
        val maxUploadBytes: Long = 2_147_483_648,
    )
}
