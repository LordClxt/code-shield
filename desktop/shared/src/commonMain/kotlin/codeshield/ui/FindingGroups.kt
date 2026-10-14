package codeshield.ui

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName

/** Mode de regroupement de l'arborescence (segmented Folders | Tools de la maquette). */
enum class GroupTab { FOLDERS, TOOLS }

/** Un groupe de l'arborescence : dossier ou moteur, avec ses findings. */
data class FindingGroup(
    val key: String,
    val title: String,
    val findings: List<Finding>,
) {
    val count: Int get() = findings.size
    val tools: List<ToolName> get() = findings.map { it.tool }.distinct().sorted()
    val fileCount: Int get() = findings.map { it.filePath }.distinct().size
}

/** Dossier parent d'un chemin (`src/a.py` -> `src`, `a.py` -> racine). */
fun parentDir(path: String): String {
    val normalized = path.replace('\\', '/')
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.ifBlank { "(root)" }
}

/**
 * Regroupe les findings pour l'arborescence + les cartes dossiers.
 * Tri : count décroissant, puis titre (stable et déterministe pour les tests).
 */
fun groupFindings(findings: List<Finding>, tab: GroupTab): List<FindingGroup> {
    val grouped: Map<String, List<Finding>> = when (tab) {
        GroupTab.FOLDERS -> findings.groupBy { parentDir(it.filePath) }
        GroupTab.TOOLS -> findings.groupBy { it.tool.name }
    }
    return grouped
        .map { (key, list) ->
            val title = if (tab == GroupTab.TOOLS) toolLabel(key) else key
            FindingGroup(key = "$tab/$key", title = title, findings = list.sortedWith(findingOrder()))
        }
        .sortedWith(compareByDescending<FindingGroup> { it.count }.thenBy { it.title })
}

private fun toolLabel(toolName: String): String =
    ToolName.entries.firstOrNull { it.name == toolName }?.let { codeshield.ui.theme.Shield.toolLabel(it) } ?: toolName

private fun findingOrder(): Comparator<Finding> =
    compareBy<Finding> { it.severity.ordinal }.thenBy { it.filePath }.thenBy { it.lineNumber }

/** Recherche plein texte (fichier, règle, titre, message OWASP/CWE). */
fun filterFindings(findings: List<Finding>, query: String): List<Finding> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return findings
    return findings.filter {
        it.filePath.lowercase().contains(q) ||
            it.ruleId.lowercase().contains(q) ||
            it.title.lowercase().contains(q) ||
            it.message?.lowercase()?.contains(q) == true
    }
}

fun countBySeverity(findings: List<Finding>): Map<Severity, Int> =
    Severity.entries.associateWith { sev -> findings.count { it.severity == sev } }
