package codeshield.cache

import codeshield.model.Finding
import kotlinx.serialization.Serializable

/**
 * Scan persisté localement (métadonnées + logs, jamais de code source).
 * Permet de retrouver ses anciens scans au redémarrage de l'app.
 */
@Serializable
data class PersistedScan(
    val projectDir: String,
    val findings: List<Finding>,
    val terminal: List<String>,
    val finishedAtEpochMs: Long,
)

@Serializable
data class PersistedScanMeta(
    val projectDir: String,
    val finishedAtEpochMs: Long,
    val findingsCount: Int,
    val terminalLines: Int,
)

/** Stockage de l'historique des scans (impl mémoire en tests, fichier sur JVM). */
interface ScanHistoryStore {
    suspend fun save(scan: PersistedScan)
    suspend fun load(projectDir: String): PersistedScan?
    suspend fun loadMeta(projectDir: String, finishedAtEpochMs: Long): PersistedScan?
    suspend fun list(): List<PersistedScanMeta>
    suspend fun clear(projectDir: String)
}

/** Impl mémoire (tests + défaut ViewModel). */
class InMemoryHistoryStore : ScanHistoryStore {
    private val store = mutableMapOf<String, MutableList<PersistedScan>>()
    private val lock = Any()

    override suspend fun save(scan: PersistedScan) {
        synchronized(lock) {
            store.getOrPut(scan.projectDir) { mutableListOf() }.add(scan)
        }
    }

    override suspend fun load(projectDir: String): PersistedScan? =
        synchronized(lock) { store[projectDir]?.maxByOrNull { it.finishedAtEpochMs } }

    override suspend fun loadMeta(projectDir: String, finishedAtEpochMs: Long): PersistedScan? =
        synchronized(lock) { store[projectDir]?.firstOrNull { it.finishedAtEpochMs == finishedAtEpochMs } }

    override suspend fun list(): List<PersistedScanMeta> =
        synchronized(lock) {
            store.flatMap { (_, scans) ->
                scans.map { s ->
                    PersistedScanMeta(s.projectDir, s.finishedAtEpochMs, s.findings.size, s.terminal.size)
                }
            }.sortedByDescending { it.finishedAtEpochMs }
        }

    override suspend fun clear(projectDir: String) {
        synchronized(lock) { store.remove(projectDir) }
    }
}
