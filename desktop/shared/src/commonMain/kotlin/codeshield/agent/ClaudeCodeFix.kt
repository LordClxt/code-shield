package codeshield.agent

import codeshield.logic.explainFinding
import codeshield.model.Finding
import codeshield.model.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Correction d'une faille par Claude Code lancé **en local** dans le dossier du projet.
 * L'app ne lit ni n'envoie le code : c'est le CLI `claude` de l'utilisateur qui ouvre
 * les fichiers et les modifie. Rien ne transite par le serveur CodeShield.
 */
object ClaudeCodeFix {

    /**
     * Outils autorisés d'office : lecture + édition de fichiers. Tout le reste (commandes
     * Bash notamment) passe par une demande de permission affichée dans l'app.
     */
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
     * Commande headless : `claude -p` en JSON dans les deux sens (protocole des SDK Claude Code).
     * - La consigne passe par l'entrée standard, jamais en argument : sous Windows, un
     *   `claude.cmd` ferait interpréter par cmd.exe le texte des scanners (`&`, `|`…).
     * - `--permission-prompt-tool stdio` : chaque outil non autorisé d'office (ex. `bun install`)
     *   arrive en `control_request` ; l'app affiche la demande et répond allow/deny.
     */
    fun command(binary: String): List<String> = listOf(
        binary, "-p",
        "--input-format", "stream-json",
        "--output-format", "stream-json",
        "--verbose",
        "--permission-prompt-tool", "stdio",
        "--permission-mode", "acceptEdits",
        "--allowedTools", ALLOWED_TOOLS.joinToString(","),
    )

    /** Premier message envoyé sur l'entrée standard : la consigne. */
    fun userMessage(prompt: String): String = buildJsonObject {
        put("type", "user")
        putJsonObject("message") {
            put("role", "user")
            put("content", prompt)
        }
    }.toString()

    /** Réponse à une demande de permission : exécuter l'outil tel que demandé. */
    fun allowResponse(requestId: String, input: JsonObject): String = controlResponse(requestId) {
        put("behavior", "allow")
        put("updatedInput", input)
    }

    /** Réponse à une demande de permission : refus, avec le motif transmis à Claude. */
    fun denyResponse(requestId: String, message: String = "L'utilisateur a refusé cette action."): String =
        controlResponse(requestId) {
            put("behavior", "deny")
            put("message", message)
        }

    private fun controlResponse(requestId: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("type", "control_response")
            putJsonObject("response") {
                put("subtype", "success")
                put("request_id", requestId)
                putJsonObject("response", body)
            }
        }.toString()

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
        appendLine("3. Si une commande est nécessaire pour que la correction prenne effet (ex. réinstaller les dépendances pour régénérer le lockfile), lance-la : l'utilisateur l'autorisera ou la refusera. Si elle est refusée, indique-la à la fin. Aucune commande destructive (suppression, push, publication).")
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

/** Événement lisible tiré de la sortie JSON d'un agent (Claude Code, Codex, OpenCode). */
sealed interface AgentEvent {
    data class Text(val text: String) : AgentEvent
    data class ToolUse(val tool: String, val target: String?) : AgentEvent
    data class Result(val summary: String, val isError: Boolean) : AgentEvent

    /** Résultat d'un outil (sortie d'une commande, échec d'une édition…). */
    data class ToolResult(val text: String, val isError: Boolean) : AgentEvent

    /** Claude Code demande la permission d'utiliser un outil (`control_request` / `can_use_tool`). */
    data class PermissionRequest(
        val requestId: String,
        val tool: String,
        val input: JsonObject,
        /** Ce qui sera exécuté : la commande pour Bash, sinon le fichier visé. */
        val summary: String,
        val description: String?,
    ) : AgentEvent

    /**
     * Commande que l'agent n'a pas pu exécuter (permission refusée d'office, bac à sable) :
     * l'app la propose à l'utilisateur, qui peut la lancer lui-même dans le projet.
     */
    data class CommandBlocked(val command: String, val reason: String) : AgentEvent

    /** Erreur bloquante : l'agent ne continuera pas (le processus peut ne pas se terminer seul). */
    data class Fatal(val message: String) : AgentEvent
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Parse une ligne de `claude -p --output-format stream-json`.
 * Retourne les événements à afficher (vide si la ligne n'est pas pertinente ou pas du JSON).
 */
fun parseClaudeStreamLine(line: String): List<AgentEvent> {
    val obj = runCatching { lenientJson.parseToJsonElement(line.trim()).jsonObject }.getOrNull() ?: return emptyList()
    return when (obj.str("type")) {
        "assistant" -> {
            val content = (obj["message"] as? JsonObject)?.get("content") as? JsonArray ?: return emptyList()
            content.mapNotNull { item ->
                val block = item as? JsonObject ?: return@mapNotNull null
                when (block.str("type")) {
                    "text" -> block.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { AgentEvent.Text(it) }
                    "tool_use" -> {
                        val input = block["input"] as? JsonObject
                        val target = input?.let { it.str("file_path") ?: it.str("path") ?: it.str("pattern") }
                        AgentEvent.ToolUse(block.str("name") ?: "tool", target)
                    }
                    else -> null
                }
            }
        }
        "user" -> {
            val content = (obj["message"] as? JsonObject)?.get("content") as? JsonArray ?: return emptyList()
            content.mapNotNull { item ->
                val block = item as? JsonObject ?: return@mapNotNull null
                if (block.str("type") != "tool_result") return@mapNotNull null
                val text = when (val c = block["content"]) {
                    is JsonArray -> c.mapNotNull { (it as? JsonObject)?.str("text") }.joinToString("\n")
                    else -> block.str("content").orEmpty()
                }
                AgentEvent.ToolResult(text.trim(), block["is_error"]?.jsonPrimitive?.booleanOrNull == true)
            }
        }
        "control_request" -> {
            val req = obj["request"] as? JsonObject ?: return emptyList()
            if (req.str("subtype") != "can_use_tool") return emptyList()
            val requestId = obj.str("request_id") ?: return emptyList()
            val tool = req.str("tool_name") ?: "tool"
            val input = req["input"] as? JsonObject ?: JsonObject(emptyMap())
            val summary = input.str("command") ?: input.str("file_path") ?: input.str("url") ?: input.str("pattern") ?: tool
            val description = req.str("description") ?: input.str("description")
            listOf(AgentEvent.PermissionRequest(requestId, tool, input, summary, description?.takeIf { it != summary }))
        }
        "result" -> listOf(
            AgentEvent.Result(
                summary = obj.str("result").orEmpty(),
                isError = obj["is_error"]?.jsonPrimitive?.booleanOrNull == true || obj.str("subtype")?.startsWith("error") == true,
            ),
        )
        else -> emptyList()
    }
}

private fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
