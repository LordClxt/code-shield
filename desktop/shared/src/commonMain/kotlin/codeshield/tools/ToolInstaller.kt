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
    /**
     * Auto-installation proposée pour les moteurs publiés en binaire autonome.
     * Exclus : Dependency-Check (JVM + base NVD), Bandit et Checkov (paquets Python,
     * utilisés seulement s'ils sont déjà installés).
     */
    val autoInstallable: Boolean
        get() = tool !in MANUAL_ONLY

    companion object {
        val MANUAL_ONLY = setOf(ToolName.DEPENDENCY_CHECK, ToolName.BANDIT, ToolName.CHECKOV)
    }
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
     * @param onProgress 0..1 (téléchargement), -1f = progression indéterminée.
     * @return chemin du binaire installé.
     */
    suspend fun install(tool: ToolName, onProgress: (Float) -> Unit): Result<String>
}
