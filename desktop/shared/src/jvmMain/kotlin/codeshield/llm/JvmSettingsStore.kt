package codeshield.llm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.attribute.PosixFilePermission

/**
 * Stocke ~/.codeshield/config.json en 0600 (clé API).
 * Tolérant : fichier absent/corrompu -> défauts.
 */
class JvmSettingsStore(
    private val configFile: File = File(System.getProperty("user.home"), ".codeshield/config.json"),
) : SettingsStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun load(): LlmSettings = runCatching {
        if (!configFile.isFile) return LlmSettings()
        json.decodeFromString<LlmSettings>(configFile.readText())
    }.getOrDefault(LlmSettings())

    override suspend fun save(settings: LlmSettings) {
        runCatching {
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(settings))
            runCatching {
                val perms = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                java.nio.file.Files.setPosixFilePermissions(configFile.toPath(), perms)
            }
        }
    }
}
