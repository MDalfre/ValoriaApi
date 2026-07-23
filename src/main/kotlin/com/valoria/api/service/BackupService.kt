package com.valoria.api.service

import com.valoria.api.config.AppProperties
import com.valoria.api.dto.BackupDto
import com.valoria.api.dto.RestoreResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.PathResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import kotlin.io.path.extension
import kotlin.io.path.name

@Service
class BackupService(
    private val properties: AppProperties,
    @Value("\${spring.datasource.username}") private val databaseUser: String,
    @Value("\${spring.datasource.password}") private val databasePassword: String,
    @Value("\${DB_HOST}") private val databaseHost: String,
    @Value("\${DB_PORT:5432}") private val databasePort: String,
    @Value("\${DB_NAME}") private val databaseName: String,
) {
    private val restoring = AtomicBoolean(false)

    fun latest(): List<BackupDto> {
        ensureDirectory(properties.backup.directory)
        return Files.list(properties.backup.directory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.name.endsWith(".dump.gz") }
                .map {
                    BackupDto(it.name, Files.size(it), Files.getLastModifiedTime(it).toInstant())
                }
                .sorted(compareByDescending<BackupDto> { it.modifiedAt })
                .limit(3)
                .toList()
        }
    }

    fun resource(fileName: String): Resource = PathResource(resolveBackup(fileName))

    fun upload(file: MultipartFile): BackupDto {
        require(!file.isEmpty) { "Backup file is empty." }
        require(file.size <= properties.backup.maxUploadBytes) { "Backup exceeds configured limit." }
        require(file.originalFilename?.endsWith(".dump.gz") == true) { "Expected a .dump.gz file." }
        ensureDirectory(properties.backup.stagingDirectory)
        ensureDirectory(properties.backup.directory)
        val temporary = properties.backup.stagingDirectory.resolve("upload-${UUID.randomUUID()}.dump.gz")
        file.inputStream.use { input -> Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING) }
        validateArchive(temporary)
        val finalName = "uploaded-${Instant.now().epochSecond}-${safeName(file.originalFilename!!)}"
        val destination = properties.backup.directory.resolve(finalName)
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
        return BackupDto(destination.name, Files.size(destination), Files.getLastModifiedTime(destination).toInstant())
    }

    fun restore(fileName: String, confirmation: String): RestoreResult {
        if (!properties.backup.restoreEnabled) {
            return RestoreResult(false, "Database restore is disabled by configuration.")
        }
        require(confirmation == "RESTORE $fileName") { "Invalid confirmation phrase." }
        if (!restoring.compareAndSet(false, true)) {
            return RestoreResult(false, "Another restore is already running.")
        }
        val archive = resolveBackup(fileName)
        return try {
            val dump = decompress(archive)
            val command = listOf(
                properties.backup.pgRestorePath,
                "--host=$databaseHost",
                "--port=$databasePort",
                "--username=$databaseUser",
                "--dbname=$databaseName",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--exit-on-error",
                dump.toString(),
            )
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply { environment()["PGPASSWORD"] = databasePassword }
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().takeLast(4_000) }
            val exitCode = process.waitFor()
            Files.deleteIfExists(dump)
            if (exitCode == 0) RestoreResult(true, "Backup restored successfully.")
            else RestoreResult(false, "pg_restore failed with exit code $exitCode: $output")
        } finally {
            restoring.set(false)
        }
    }

    private fun validateArchive(archive: Path) {
        val dump = decompress(archive)
        try {
            val process = ProcessBuilder(properties.backup.pgRestorePath, "--list", dump.toString())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }
            require(process.waitFor() == 0) { "Invalid PostgreSQL custom-format backup." }
        } finally {
            Files.deleteIfExists(dump)
        }
    }

    private fun decompress(archive: Path): Path {
        ensureDirectory(properties.backup.stagingDirectory)
        val dump = properties.backup.stagingDirectory.resolve("${UUID.randomUUID()}.dump")
        GZIPInputStream(BufferedInputStream(Files.newInputStream(archive))).use { input ->
            BufferedOutputStream(Files.newOutputStream(dump)).use { output -> input.copyTo(output) }
        }
        return dump
    }

    private fun resolveBackup(fileName: String): Path {
        require(fileName == safeName(fileName) && fileName.endsWith(".dump.gz")) { "Invalid backup name." }
        val root = properties.backup.directory.toAbsolutePath().normalize()
        val file = root.resolve(fileName).normalize()
        require(file.startsWith(root) && Files.isRegularFile(file)) { "Backup not found." }
        return file
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(180)

    private fun ensureDirectory(path: Path) {
        Files.createDirectories(path)
    }
}

