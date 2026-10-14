package codeshield.ui

import codeshield.model.Finding
import codeshield.model.ToolName
import codeshield.scanner.ScannerConfig
import codeshield.tools.ToolStatus
import kotlinx.coroutines.flow.Flow

/** Ligne du terminal pipeline (STDOUT/STDERR/sortie). */
sealed interface TerminalLine {
    data class Out(val text: String) : TerminalLine
    data class Err(val text: String) : TerminalLine
    data class Exit(val command: String, val code: Int) : TerminalLine
}

/** Exécution d'une commande CLI diffusée en continu (adaptée en jvmMain via ProcessRunner). */
typealias CommandRunner = (command: List<String>, workDir: String?) -> Flow<TerminalLine>

/**
 * Session interactive : chaque ligne envoyée dans [input] est écrite sur l'entrée standard
 * (consigne puis réponses aux permissions de Claude Code). Fermer le canal ferme l'entrée.
 */
typealias InteractiveCommandRunner =
    (command: List<String>, workDir: String?, input: kotlinx.coroutines.channels.ReceiveChannel<String>) -> Flow<TerminalLine>

/** Lecture locale d'un fichier en lignes (Local Diff Viewer — jamais envoyée au réseau). */
typealias FileReader = (path: String) -> List<String>

/** Localise un binaire (`semgrep`, `gitleaks`, …) -> chemin absolu ou null. */
typealias BinaryLocator = (binary: String) -> String?

/** Vérifie qu'un dossier projet existe (pré-vol, remplace le crash natif). */
typealias DirChecker = (dir: String) -> Boolean

/** Fabrique du client de synchro à partir de l'URL serveur + token. */
typealias SyncClientFactory = (baseUrl: String, token: String) -> codeshield.sync.SyncClient

/** Profil du projet (extensions, manifestes) pour l'auto-détection des moteurs. */
typealias ProjectProfiler = (dir: String) -> codeshield.scanner.ProjectProfile

/** Dossiers temporaires des rapports de scan (jamais dans le dépôt scanné). */
class ScanWorkDirs(
    val create: () -> String = { "/tmp/codeshield-scan" },
    val delete: (String) -> Unit = {},
)

/** Avancement d'un moteur pendant un scan (affiché par moteur dans l'UI). */
enum class EngineState { QUEUED, RUNNING, DONE, FAILED, TIMEOUT, MISSING, SKIPPED, CANCELLED }

data class EngineRun(
    val tool: ToolName,
    val state: EngineState,
    val findings: Int = 0,
    val durationMs: Long? = null,
    val message: String? = null,
)

/** Projet récemment consulté (menu déroulant sous le titre). */
data class ConsultedProject(
    val path: String,
    val name: String = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path },
    val findingsCount: Int? = null,
    val lastScanEpochMs: Long? = null,
)

/** Session d'un onglet de projet/workspace. */
data class WorkspaceTabState(
    val id: String = "tab-1",
    val projectDir: String = "",
    val findings: List<Finding> = emptyList(),
    val terminal: List<String> = emptyList(),
    val isScanning: Boolean = false,
    val selected: Finding? = null,
    val fileLines: List<String> = emptyList(),
    val lastScanAt: Long? = null,
    val restoreMessage: String? = null,
    val pentestReport: codeshield.pentest.PentestReport? = null,
    val pentestAnalysis: codeshield.pentest.ProjectAnalysis? = null,
    val isPentesting: Boolean = false,
    val pentestProgress: String? = null,
    val aiExplanation: String? = null,
    val aiLoading: Boolean = false,
    val aiError: String? = null,
    val aiTriage: String? = null,
    val aiTriageLoading: Boolean = false,
    val aiTriageError: String? = null,
    val serverUrl: String = "http://localhost:8000",
    val customStartCommand: String = "",
    val pentestOfflineMode: Boolean = false,
) {
    val title: String get() = displayTitle("Untitled Project")

    fun displayTitle(untitled: String = "Untitled Project"): String {
        val clean = projectDir.trimEnd('/', '\\')
        return if (clean.isBlank()) untitled else clean.substringAfterLast('/').substringAfterLast('\\')
    }
}

/** Demande de permission de Claude Code (ex. lancer `bun install`). */
data class ClaudePermission(
    val requestId: String,
    val tool: String,
    /** Ce qui sera exécuté : la commande pour Bash, sinon le fichier visé. */
    val summary: String,
    val description: String? = null,
    /** Entrée de l'outil, renvoyée telle quelle si l'utilisateur accepte. */
    val input: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
)

/** Commande que l'agent n'a pas pu exécuter ; l'utilisateur peut la lancer depuis l'app. */
data class BlockedCommand(
    val id: Int,
    val command: String,
    val reason: String,
    val running: Boolean = false,
    /** Code de sortie une fois lancée par l'utilisateur. */
    val exitCode: Int? = null,
)

/** Correction d'une faille par Claude Code (CLI local, lancé dans le dossier du projet). */
data class ClaudeFixState(
    /** Une faille (onglet Claude Code) ou un lot CRITICAL/HIGH (bandeau au-dessus de la liste). */
    val findings: List<Finding>,
    val running: Boolean = true,
    /** Journal lisible : texte de Claude, fichiers lus/modifiés. */
    val log: List<String> = emptyList(),
    /** Résumé final de Claude (ou cause de l'échec). */
    val summary: String? = null,
    val success: Boolean? = null,
    /** Demandes de permission en attente (la première est affichée). */
    val pendingPermissions: List<ClaudePermission> = emptyList(),
    /** « Tout autoriser pour cette correction » : plus de demande jusqu'à la fin de la session. */
    val approveAll: Boolean = false,
    /** Agent qui mène la correction. */
    val agent: codeshield.agent.CodingAgentKind = codeshield.agent.CodingAgentKind.CLAUDE_CODE,
    /** Commandes que l'agent n'a pas pu lancer, proposées à l'utilisateur (Codex, OpenCode). */
    val blockedCommands: List<BlockedCommand> = emptyList(),
) {
    val isBatch: Boolean get() = findings.size > 1
}

/** État complet de l'écran principal. */
data class ScannerUiState(
    val tabs: List<WorkspaceTabState> = listOf(WorkspaceTabState(id = "tab-1")),
    val activeTabId: String = "tab-1",
    val projectDir: String = "",
    /** Moteurs cochés (registre ScannerEngines). */
    val enabledEngines: Set<ToolName> = codeshield.scanner.ScannerEngines.defaultEnabled,
    /** Ignore automatiquement les moteurs sans cible (ex. OSV-Scanner sans lockfile). */
    val autoDetectEngines: Boolean = true,
    /** Avancement par moteur du scan en cours / dernier scan. */
    val engineRuns: Map<ToolName, EngineRun> = emptyMap(),
    /** Erreur de lecture du fichier sélectionné (visualiseur de code). */
    val fileError: String? = null,
    val serverUrl: String = "http://localhost:8000",
    val customStartCommand: String = "",
    val pentestOfflineMode: Boolean = false,
    val apiToken: String = "",
    val projectId: String = "",
    val isScanning: Boolean = false,
    val terminal: List<String> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val selected: Finding? = null,
    val fileLines: List<String> = emptyList(),
    val syncMessage: String? = null,
    /** Statuts des moteurs gérés (rafraîchis au démarrage + après install). */
    val toolStatuses: Map<ToolName, ToolStatus> = emptyMap(),
    /** Historique des scans persistés (survit au redémarrage). */
    val history: List<codeshield.cache.PersistedScanMeta> = emptyList(),
    /** Timestamp du dernier scan affiché (persisté ou live). */
    val lastScanAt: Long? = null,
    /** Message de restore (ex. "Scan du 30/09 restauré"). */
    val restoreMessage: String? = null,
    /** Réglages LLM + thème (menu Settings, persistés disque). */
    val llmSettings: codeshield.llm.LlmSettings = codeshield.llm.LlmSettings(),
    /** Test de connexion LLM en cours (spinner dans Settings). */
    val llmTesting: Boolean = false,
    /** Dernier résultat du test de connexion (affiché inline, vert/rouge). */
    val llmTestMessage: String? = null,
    val llmTestOk: Boolean? = null,
    /** Fetch des modèles disponibles en cours (spinner dans Settings). */
    val modelsLoading: Boolean = false,
    /** Modèles récupérés en live depuis l'API du provider (vide = liste statique). */
    val fetchedModels: List<String> = emptyList(),
    /** Erreur éventuelle du fetch des modèles. */
    val modelsMessage: String? = null,
    /** Explication IA de la finding sélectionnée (metadata-only, fallback local sinon). */
    val aiExplanation: String? = null,
    val aiLoading: Boolean = false,
    val aiError: String? = null,
    /** Triage IA projet (top priorités, faux positifs). */
    val aiTriage: String? = null,
    val aiTriageLoading: Boolean = false,
    /** Erreur du triage IA (affichée inline — avant : uniquement au terminal). */
    val aiTriageError: String? = null,
    /** État du backend local (running/stopped/starting). */
    val backendState: codeshield.backend.BackendState = codeshield.backend.BackendState(),
    /** Vérification santé en cours (spinner sur "Vérifier"). */
    val isCheckingBackend: Boolean = false,
    /** Rapport du dernier test de pénétration backend. */
    val pentestReport: codeshield.pentest.PentestReport? = null,
    /** Audit de pénétration en cours (spinner). */
    val isPentesting: Boolean = false,
    /** Message d'avancement du pentest en cours. */
    val pentestProgress: String? = null,
    /** Analyse du projet sélectionné (effectuée avant chaque pentest). */
    val pentestAnalysis: codeshield.pentest.ProjectAnalysis? = null,
    /** Analyse du projet en cours (spinner sur "Analyser"). */
    val isAnalyzing: Boolean = false,
    /** Agents de code installés (chemin du binaire, null = absent). */
    val agentPaths: Map<codeshield.agent.CodingAgentKind, String?> = emptyMap(),
    /** Dernière correction Claude Code lancée (affichée si la faille est sélectionnée). */
    val claudeFix: ClaudeFixState? = null,
) {
    val activeTab: WorkspaceTabState
        get() = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull() ?: WorkspaceTabState()

    fun toScannerConfig(): ScannerConfig = ScannerConfig(
        projectDir = projectDir,
        enabled = enabledEngines,
        autoDetect = autoDetectEngines,
    )
}
