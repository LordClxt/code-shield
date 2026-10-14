package codeshield.backend

import kotlinx.coroutines.flow.StateFlow

data class BackendState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val port: Int = 8000,
    val url: String = "http://localhost:8000",
    val error: String? = null,
    val message: String? = null,
    val pid: Long? = null,
    /** Dossier projet effectivement démarré (le projet sélectionné, pas forcément CodeShield). */
    val projectDir: String? = null,
    /** Stack détectée (ex. "FastAPI CodeShield", "Node/Vite", "Python/FastAPI"). */
    val stackLabel: String? = null,
    /** Commande effective utilisée pour lancer le backend. */
    val effectiveCommand: List<String> = emptyList(),
    /** Logs complets (stdout + stderr) du processus capturés lors du crash. */
    val rawLogs: String? = null,
    /** Code de sortie du processus (si terminé en erreur). */
    val exitCode: Int? = null,
    /** Diagnostic structuré de la panne (règles locales ou LLM). */
    val diagnostic: BackendDiagnostic? = null,
    /** Indicateur de diagnostic IA en cours. */
    val isDiagnosing: Boolean = false,
)

interface BackendRunner {
    val state: StateFlow<BackendState>
    suspend fun checkHealth(url: String = "http://localhost:8000"): Boolean
    suspend fun startBackend(
        customDir: String? = null,
        port: Int = 8000,
        customCommand: List<String>? = null,
    ): Result<Unit>
    suspend fun stopBackend(): Result<Unit>
}

object NoopBackendRunner : BackendRunner {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(BackendState(running = true))
    override val state: StateFlow<BackendState> = _state
    override suspend fun checkHealth(url: String): Boolean = true
    override suspend fun startBackend(customDir: String?, port: Int, customCommand: List<String>?): Result<Unit> = Result.success(Unit)
    override suspend fun stopBackend(): Result<Unit> = Result.success(Unit)
}
