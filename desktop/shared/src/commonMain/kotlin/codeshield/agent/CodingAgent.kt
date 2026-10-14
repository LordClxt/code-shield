package codeshield.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Agents de code pris en charge : chacun est un CLI installé par l'utilisateur et lancé en local. */
@Serializable
enum class CodingAgentKind(
    val label: String,
    val binary: String,
    val installUrl: String,
    /** L'agent peut demander la permission en cours de route (carte Autoriser / Refuser). */
    val asksPermission: Boolean,
) {
    CLAUDE_CODE("Claude Code", "claude", "https://claude.com/claude-code", asksPermission = true),
    CODEX("Codex", "codex", "https://developers.openai.com/codex/cli", asksPermission = false),
    OPENCODE("OpenCode", "opencode", "https://opencode.ai", asksPermission = false),
}

/**
 * Adaptateur d'un CLI d'agent : commande headless, ce qui part sur l'entrée standard,
 * et lecture de sa sortie JSON (une ligne = un événement).
 */
interface CodingAgent {
    val kind: CodingAgentKind

    /** Commande à lancer dans le dossier du projet. [autoApprove] : exécuter les commandes sans demander. */
    fun command(binary: String, autoApprove: Boolean): List<String>

    /** Lignes écrites sur l'entrée standard au démarrage (la consigne passe toujours par là). */
    fun initialInput(prompt: String): List<String>

    /** L'entrée reste ouverte pour répondre aux permissions (sinon fermée après la consigne). */
    val keepsInputOpen: Boolean

    /** Événements tirés d'une ligne de sortie. [autoApprove] = mode de la session. */
    fun parse(line: String, autoApprove: Boolean): List<AgentEvent>

    companion object {
        fun of(kind: CodingAgentKind): CodingAgent = when (kind) {
            CodingAgentKind.CLAUDE_CODE -> ClaudeCodeAgent
            CodingAgentKind.CODEX -> CodexAgent
            CodingAgentKind.OPENCODE -> OpenCodeAgent
        }
    }
}

/** Claude Code : protocole JSON bidirectionnel, permissions demandées en direct. */
object ClaudeCodeAgent : CodingAgent {
    override val kind = CodingAgentKind.CLAUDE_CODE
    override val keepsInputOpen = true
    override fun command(binary: String, autoApprove: Boolean) = ClaudeCodeFix.command(binary)
    override fun initialInput(prompt: String) = listOf(ClaudeCodeFix.userMessage(prompt))
    override fun parse(line: String, autoApprove: Boolean) = parseClaudeStreamLine(line)
}

/**
 * Codex (`codex exec --json`). Il ne peut pas demander de permission en mode non interactif :
 * ses commandes tournent dans son bac à sable « workspace-write ». Sans auto-approbation, le
 * réseau y est coupé (ex. `bun install` échoue) : les commandes en échec sont proposées à
 * l'utilisateur. Avec, le réseau est ouvert dans le bac à sable.
 */
object CodexAgent : CodingAgent {
    override val kind = CodingAgentKind.CODEX
    override val keepsInputOpen = false

    override fun command(binary: String, autoApprove: Boolean): List<String> = buildList {
        addAll(listOf(binary, "exec", "--json", "--skip-git-repo-check", "--color", "never", "--sandbox", "workspace-write"))
        if (autoApprove) addAll(listOf("-c", "sandbox_workspace_write.network_access=true"))
        add("-") // consigne lue sur l'entrée standard
    }

    override fun initialInput(prompt: String) = listOf(prompt)

    override fun parse(line: String, autoApprove: Boolean): List<AgentEvent> {
        val obj = line.asJsonObject() ?: return emptyList()
        return when (obj.str("type")) {
            "item.started" -> {
                val item = obj["item"] as? JsonObject ?: return emptyList()
                when (item.str("type")) {
                    "command_execution" -> listOf(AgentEvent.ToolUse("Bash", item.str("command")))
                    else -> emptyList()
                }
            }
            "item.completed" -> {
                val item = obj["item"] as? JsonObject ?: return emptyList()
                when (item.str("type")) {
                    "agent_message" -> listOfNotNull(item.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { AgentEvent.Text(it) })
                    "file_change" -> (item["changes"] as? JsonArray).orEmpty().mapNotNull { c ->
                        (c as? JsonObject)?.str("path")?.let { AgentEvent.ToolUse("Edit", it) }
                    }
                    "command_execution" -> {
                        val command = item.str("command").orEmpty()
                        val output = item.str("aggregated_output").orEmpty().trim()
                        val exit = (item["exit_code"])?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
                        val failed = item.str("status") in setOf("failed", "declined") || (exit != null && exit != 0)
                        buildList {
                            add(AgentEvent.ToolResult(output, failed))
                            if (failed && !autoApprove && command.isNotBlank()) {
                                add(AgentEvent.CommandBlocked(command, "Échec dans le bac à sable de Codex (réseau coupé)."))
                            }
                        }
                    }
                    "error" -> listOfNotNull(item.str("message")?.let { AgentEvent.ToolResult(it, true) })
                    else -> emptyList()
                }
            }
            "turn.completed" -> listOf(AgentEvent.Result("", isError = false))
            "turn.failed" -> listOf(AgentEvent.Fatal((obj["error"] as? JsonObject)?.str("message") ?: "Codex a échoué."))
            "error" -> listOfNotNull(obj.str("message")?.let { AgentEvent.ToolResult(it, true) })
            else -> emptyList()
        }
    }
}

/**
 * OpenCode (`opencode run --format json`). En mode non interactif, toute permission demandée
 * est refusée d'office, sauf avec `--auto` : les commandes refusées sont proposées à l'utilisateur.
 */
object OpenCodeAgent : CodingAgent {
    override val kind = CodingAgentKind.OPENCODE
    override val keepsInputOpen = false

    override fun command(binary: String, autoApprove: Boolean): List<String> = buildList {
        addAll(listOf(binary, "run", "--format", "json"))
        if (autoApprove) add("--auto")
    }

    override fun initialInput(prompt: String) = listOf(prompt)

    override fun parse(line: String, autoApprove: Boolean): List<AgentEvent> {
        val obj = line.asJsonObject() ?: return emptyList()
        val part = obj["part"] as? JsonObject
        return when (obj.str("type")) {
            "text" -> listOfNotNull(part?.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { AgentEvent.Text(it) })
            "tool_use" -> {
                part ?: return emptyList()
                val tool = part.str("tool") ?: "tool"
                val state = part["state"] as? JsonObject
                val input = state?.get("input") as? JsonObject
                val command = input?.str("command")
                val target = command ?: input?.let { it.str("filePath") ?: it.str("file_path") ?: it.str("path") ?: it.str("pattern") }
                val label = if (tool == "bash") "Bash" else tool.replaceFirstChar { it.uppercase() }
                if (state?.str("status") == "error") {
                    val error = state.str("error").orEmpty()
                    if (command != null && ("reject" in error.lowercase() || "permission" in error.lowercase())) {
                        listOf(AgentEvent.ToolUse(label, target), AgentEvent.CommandBlocked(command, "Permission refusée d'office par OpenCode."))
                    } else {
                        listOf(AgentEvent.ToolUse(label, target), AgentEvent.ToolResult(error, true))
                    }
                } else {
                    val output = if (tool == "bash") state?.str("output").orEmpty().trim() else ""
                    listOfNotNull(AgentEvent.ToolUse(label, target), output.takeIf { it.isNotEmpty() }?.let { AgentEvent.ToolResult(it, false) })
                }
            }
            "error" -> {
                val err = obj["error"] as? JsonObject
                val message = (err?.get("data") as? JsonObject)?.str("message") ?: err?.str("name") ?: "OpenCode a échoué."
                listOf(AgentEvent.Fatal(message))
            }
            else -> emptyList()
        }
    }
}

private val agentJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun String.asJsonObject(): JsonObject? =
    runCatching { agentJson.parseToJsonElement(trim()).jsonObject }.getOrNull()

private fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
