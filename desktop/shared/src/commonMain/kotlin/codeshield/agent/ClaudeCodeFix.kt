package codeshield.agent

import codeshield.logic.explainFinding
import codeshield.model.Finding
import codeshield.model.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Correction d'une faille par Claude Code lancé **en local** dans le dossier du projet.
 * L'app ne lit ni n'envoie le code : c'est le CLI `claude` de l'utilisateur qui ouvre
 * les fichiers et les modifie. Rien ne transite par le serveur CodeShield.
 */
object ClaudeCodeFix {

    /** Outils autorisés : lecture + édition de fichiers. Pas de Bash (aucune commande lancée). */
    val ALLOWED_TOOLS = listOf("Read", "Edit", "MultiEdit", "Write", "Glob", "Grep")

    /** Délai max d'une correction (au-delà, le processus est tué). */
    const val TIMEOUT_MS: Long = 15 * 60_000

    /** Délai max d'une correction groupée. */
    const val BATCH_TIMEOUT_MS: Long = 45 * 60_000

    /** Nombre max de failles confiées en une seule session (consigne lisible, session bornée). */
    const val MAX_BATCH = 25

    /** Délai à accorder à Claude Code selon le nombre de failles. */
    fun timeoutFor(count: Int): Long = if (count > 1) BATCH_TIMEOUT_MS else TIMEOUT_MS

    /**
     * Commande headless : `claude -p` avec éditions acceptées et sortie JSON en flux.
     * La consigne passe par l'entrée standard, jamais en argument : sous Windows, un
     * `claude.cmd` ferait interpréter par cmd.exe le texte des scanners (`&`, `|`…).
     */
    fun command(binary: String): List<String> = listOf(
        binary, "-p",
        "--permission-mode", "acceptEdits",
        "--allowedTools", ALLOWED_TOOLS.joinToString(","),
        "--output-format", "stream-json",
        "--verbose",
    )

    /** Failles à corriger en lot : CRITICAL puis HIGH, une par emplacement, au plus [MAX_BATCH]. */
    fun batchTargets(findings: List<Finding>): List<Finding> = findings
        .filter { it.severity == Severity.CRITICAL || it.severity == Severity.HIGH }
        .distinctBy { listOf(it.ruleId, it.filePath, it.lineNumber) }
        .sortedWith(compareBy({ it.severity.ordinal }, { it.filePath }, { it.lineNumber }))
        .take(MAX_BATCH)

    /** Consigne envoyée à Claude Code : métadonnées de la ou des failles + guide local. */
    fun prompt(findings: List<Finding>): String = buildString {
        require(findings.isNotEmpty()) { "findings must not be empty" }
        if (findings.size == 1) {
            appendLine("Corrige cette faille de sécurité détectée par CodeShield dans le dépôt courant.")
            appendLine()
            describe(findings.single(), indent = "")
            appendLine()
            appendLine("Piste de correction :")
            appendLine(explainFinding(findings.single()).fix)
        } else {
            appendLine("Corrige ces ${findings.size} failles de sécurité détectées par CodeShield dans le dépôt courant, de la plus grave à la moins grave.")
            findings.forEachIndexed { i, f ->
                appendLine()
                appendLine("${i + 1}. ${f.severity.name} — ${f.title}")
                describe(f, indent = "   ")
                appendLine("   - Piste : ${explainFinding(f).fix.lines().joinToString(" ")}")
            }
        }
        appendLine()
        appendLine("Consignes :")
        appendLine("1. Lis chaque fichier et son contexte avant de modifier quoi que ce soit.")
        appendLine("2. Fais la correction minimale qui supprime la faille sans changer le comportement attendu.")
        appendLine("3. Tu ne peux pas lancer de commandes : si une étape en demande (lockfile, migration, révocation de secret), indique-la à la fin.")
        appendLine("4. Si c'est un faux positif, ne modifie rien et explique pourquoi.")
        append("5. Termine par un résumé court" + (if (findings.size > 1) " par faille (numéro, corrigée / faux positif / action manuelle)" else "") + " : fichiers modifiés et ce qui a changé.")
    }

    fun prompt(finding: Finding): String = prompt(listOf(finding))

    private fun StringBuilder.describe(f: Finding, indent: String) {
        val range = if (f.lastLine != f.lineNumber) "${f.lineNumber}-${f.lastLine}" else "${f.lineNumber}"
        appendLine("$indent- Fichier : ${f.filePath} (lignes $range)")
        if (indent.isEmpty()) {
            appendLine("- Sévérité : ${f.severity.name}")
            appendLine("- Titre : ${f.title}")
        }
        appendLine("$indent- Outil / règle : ${f.tool.name} / ${f.ruleId}")
        f.message?.takeIf { it.isNotBlank() }?.let { appendLine("$indent- Message du scanner : $it") }
        if (f.cwe.isNotEmpty()) appendLine("$indent- CWE : ${f.cwe.joinToString(", ")}")
        if (f.owasp.isNotEmpty()) appendLine("$indent- OWASP : ${f.owasp.joinToString(", ")}")
        f.packageName?.let { pkg ->
            append("$indent- Dépendance : $pkg")
            f.installedVersion?.let { append(" @ $it") }
            f.fixedVersion?.let { append(" (corrigée en $it)") }
            appendLine()
        }
    }
}

/** Événement lisible tiré d'une ligne `stream-json` de Claude Code. */
sealed interface ClaudeEvent {
    data class Text(val text: String) : ClaudeEvent
    data class ToolUse(val tool: String, val target: String?) : ClaudeEvent
    data class Result(val summary: String, val isError: Boolean) : ClaudeEvent
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Parse une ligne de `claude -p --output-format stream-json`.
 * Retourne les événements à afficher (vide si la ligne n'est pas pertinente ou pas du JSON).
 */
fun parseClaudeStreamLine(line: String): List<ClaudeEvent> {
    val obj = runCatching { lenientJson.parseToJsonElement(line.trim()).jsonObject }.getOrNull() ?: return emptyList()
    return when (obj.str("type")) {
        "assistant" -> {
            val content = (obj["message"] as? JsonObject)?.get("content") as? JsonArray ?: return emptyList()
            content.mapNotNull { item ->
                val block = item as? JsonObject ?: return@mapNotNull null
                when (block.str("type")) {
                    "text" -> block.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { ClaudeEvent.Text(it) }
                    "tool_use" -> {
                        val input = block["input"] as? JsonObject
                        val target = input?.let { it.str("file_path") ?: it.str("path") ?: it.str("pattern") }
                        ClaudeEvent.ToolUse(block.str("name") ?: "tool", target)
                    }
                    else -> null
                }
            }
        }
        "result" -> listOf(
            ClaudeEvent.Result(
                summary = obj.str("result").orEmpty(),
                isError = obj["is_error"]?.jsonPrimitive?.booleanOrNull == true || obj.str("subtype")?.startsWith("error") == true,
            ),
        )
        else -> emptyList()
    }
}

private fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
