package codeshield.scanner

import codeshield.model.ToolName
import kotlinx.serialization.Serializable

/** Configuration des moteurs de scan (Project Scanner). */
@Serializable
data class ScannerConfig(
    val projectDir: String,
    val enabled: Set<ToolName> = ScannerEngines.defaultEnabled,
    /** Ignore les moteurs sans rien à analyser (ex. OSV-Scanner sans lockfile). */
    val autoDetect: Boolean = true,
    val semgrepConfig: String = DEFAULT_SEMGREP_CONFIG,
) {
    /** Moteurs à lancer pour ce projet, dans l'ordre du registre. */
    fun enginesFor(profile: ProjectProfile): List<ScannerEngine> = ScannerEngines.all.filter {
        it.tool in enabled && (!autoDetect || it.isRelevant(profile))
    }
}
