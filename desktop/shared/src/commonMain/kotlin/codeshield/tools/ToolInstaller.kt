package codeshield.tools

import codeshield.model.ToolName

/** État d'un moteur côté poste utilisateur. */
enum class ToolState {
    /** Binaire résolu (géré par l'app ou système). */
    READY,
    /** Absent : téléchargement possible (sauf Dependency-Check). */
    MISSING,
    /** Téléchargement / installation en cours. */
    INSTALLING,
}

/** Statut observable d'un moteur (état + progression + erreur éventuelle). */
data class ToolStatus(
    val tool: ToolName,
    val state: ToolState,
    val path: String? = null,
    val progress: Float? = null,
    val error: String? = null,
) {
    /** Auto-installation proposée pour tous les moteurs sauf Dependency-Check (JVM + base NVD). */
    val autoInstallable: Boolean
        get() = tool != ToolName.DEPENDENCY_CHECK
}

/**
 * Contrat d'installation gérée (implémenté en jvmMain).
 * `~/.codeshield/bin` est prioritaire ; le PATH système sert de fallback.
 */
interface ToolInstaller {
    /** Chemin du binaire géré par l'app, ou null si non installé par elle. */
    fun installedPath(tool: ToolName): String?

    /** Statut courant (ne télécharge jamais). */
    suspend fun status(tool: ToolName): ToolStatus

    /**
     * Télécharge + vérifie + installe le moteur.
     * @param onProgress 0..1 pendant le téléchargement (ou [InstallProgress.UNKNOWN_SIZE]),
     *   puis [InstallProgress.VERIFYING]. Pas d'appel à 1f final : le moteur est prêt
     *   quand `install` retourne.
     * @return chemin du binaire installé.
     */
    suspend fun install(tool: ToolName, onProgress: (Float) -> Unit): Result<String>
}

/** Valeurs spéciales de progression d'installation (hors de 0..1). */
object InstallProgress {
    /** Téléchargement fini : vérification SHA-256 et installation, durée inconnue. */
    const val VERIFYING: Float = -1f

    /** Téléchargement en cours, taille non annoncée par le serveur. */
    const val UNKNOWN_SIZE: Float = -2f
}
