package codeshield.cache

import codeshield.model.Finding

/** Rapport local mis en cache (sessions + derniers résultats). */
data class CachedReport(
    val projectDir: String,
    val findings: List<Finding>,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

/** Stockage local des rapports. */
interface LocalReportCache {
    suspend fun save(report: CachedReport)
    suspend fun load(projectDir: String): CachedReport?
    suspend fun clear(projectDir: String)
}

/**
 * Implémentation mémoire (v1). SQLDelight prendra le relais en v2 pour
 * la persistance disque — l'interface reste inchangée.
 */
class InMemoryReportCache : LocalReportCache {
    private val store = mutableMapOf<String, CachedReport>()
    private val lock = Any()

    override suspend fun save(report: CachedReport) {
        synchronized(lock) { store[report.projectDir] = report }
    }

    override suspend fun load(projectDir: String): CachedReport? =
        synchronized(lock) { store[projectDir] }

    override suspend fun clear(projectDir: String) {
        synchronized(lock) { store.remove(projectDir) }
    }
}
