package codeshield.backend

/**
 * Catégorie de panne rencontrée lors du démarrage du backend.
 */
enum class FailureCategory(val title: String) {
    DATABASE_NOT_FOUND("Base de données inexistante / non créée"),
    DATABASE_UNREACHABLE("Serveur de base de données inaccessible"),
    PORT_IN_USE("Port réseau déjà utilisé par un autre processus"),
    MISSING_DEPENDENCY("Dépendance logicielle ou package manquant"),
    MIGRATION_PENDING("Migrations SQL / Schéma de base non migré"),
    RUNTIME_EXCEPTION("Erreur / Exception du code applicatif"),
    ENVIRONMENT_ERROR("Configuration ou variables d'environnement (.env) invalides"),
    PERMISSION_DENIED("Permission refusée ou binaire non exécutable"),
    GENERIC_CRASH("Échec inattendu du processus backend"),
}

/**
 * Action de correction rapide exécutable directement en 1 clic par l'utilisateur.
 */
data class QuickFixAction(
    val id: String,
    val label: String,
    val command: List<String>,
    val description: String,
    val autoRestartBackend: Boolean = true,
    val isDestructive: Boolean = false,
)

/**
 * Origine du diagnostic : règles expertes locales (instantané) ou analyse LLM approfondie.
 */
enum class DiagnosticSource(val label: String) {
    LOCAL_RULE("Diagnostic Expert Local"),
    LLM("Analyse Assistée par IA"),
}

/**
 * Diagnostic complet d'un échec de démarrage de backend.
 */
data class BackendDiagnostic(
    val category: FailureCategory,
    val title: String,
    val rootCause: String,
    val explanation: String,
    val suggestedFixes: List<String> = emptyList(),
    val quickFixActions: List<QuickFixAction> = emptyList(),
    val source: DiagnosticSource = DiagnosticSource.LOCAL_RULE,
    val rawLogs: String? = null,
    val detectedServiceOrDb: String? = null,
)
