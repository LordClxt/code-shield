package codeshield.cache

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Persistance disque des scans dans `~/.codeshield/scans/` :
 * un fichier JSON par scan (`<slug>_<timestamp>.json`), métadonnées uniquement.
 * Survit au redémarrage de l'app (contrairement à InMemoryReportCache).
 */
class JvmFileHistoryStore(
    private val scansDir: File = File(System.getProperty("user.home"), ".codeshield/scans"),
    private val maxTerminalLines: Int = 1000,
    private val maxScansPerProject: Int = 20,
) : ScanHistoryStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun slug(projectDir: String): String {
        val base = projectDir.substringAfterLast('/').substringAfterLast('\\').take(40)
            .map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").ifBlank { "scan" }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(projectDir.toByteArray()).joinToString("") { "%02x".format(it) }.take(8)
        return "${base}_$hash"
    }

    private fun fileFor(projectDir: String, finishedAt: Long): File =
        File(scansDir, "${slug(projectDir)}_${finishedAt}.json")

    override suspend fun save(scan: PersistedScan) {
        runCatching {
            scansDir.mkdirs()
            val trimmed = scan.copy(terminal = scan.terminal.takeLast(maxTerminalLines))
            fileFor(scan.projectDir, scan.finishedAtEpochMs).writeText(json.encodeToString(trimmed))
            prune(scan.projectDir)
        }
    }

    override suspend fun load(projectDir: String): PersistedScan? = runCatching {
        if (!scansDir.isDirectory) return null
        val prefix = "${slug(projectDir)}_"
        scansDir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".json") }
            ?.maxByOrNull { it.name }?.let { json.decodeFromString<PersistedScan>(it.readText()) }
    }.getOrNull()

    override suspend fun loadMeta(projectDir: String, finishedAtEpochMs: Long): PersistedScan? = runCatching {
        fileFor(projectDir, finishedAtEpochMs).takeIf { it.isFile }
            ?.let { json.decodeFromString<PersistedScan>(it.readText()) }
    }.getOrNull()

    override suspend fun list(): List<PersistedScanMeta> = runCatching {
        if (!scansDir.isDirectory) return emptyList()
        scansDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching {
                // Lecture légère : on décode tout (fichiers < quelques centaines de Ko).
                val s = json.decodeFromString<PersistedScan>(f.readText())
                PersistedScanMeta(s.projectDir, s.finishedAtEpochMs, s.findings.size, s.terminal.size)
            }.getOrNull()
        }.sortedByDescending { it.finishedAtEpochMs }
    }.getOrDefault(emptyList())

    override suspend fun clear(projectDir: String) {
        runCatching {
            val prefix = "${slug(projectDir)}_"
            scansDir.listFiles { f -> f.isFile && f.name.startsWith(prefix) }?.forEach { it.delete() }
        }
    }

    private fun prune(projectDir: String) {
        val prefix = "${slug(projectDir)}_"
        val files = scansDir.listFiles { f -> f.isFile && f.name.startsWith(prefix) }
            ?.sortedBy { it.name }.orEmpty()
        if (files.size > maxScansPerProject) {
            files.take(files.size - maxScansPerProject).forEach { it.delete() }
        }
    }
}
