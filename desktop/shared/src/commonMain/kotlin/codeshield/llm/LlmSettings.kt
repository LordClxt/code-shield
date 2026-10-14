package codeshield.llm

import kotlinx.serialization.Serializable

/** Fournisseurs LLM supportés (tous via HTTPS, clé stockée locale uniquement). */
enum class LlmProvider(val label: String) {
    NONE("Désactivé (local uniquement)"),
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic Claude"),
    GEMINI("Google Gemini"),
    MISTRAL("Mistral"),
    OLLAMA("Ollama (local, sans clé)"),
    OPENROUTER("OpenRouter (multi-modèles)"),
    CUSTOM("OpenAI-compatible (URL custom)"),
}

/** Thème de l'app (menu Configuration). */
enum class AppTheme(val label: String) {
    DARK("Sombre"),
    LIGHT("Clair"),
}

/**
 * Réglages LLM + thème. La clé API ne quitte jamais la machine
 * sauf vers l'API du provider choisi (HTTPS). Jamais loggée.
 */
@Serializable
data class LlmSettings(
    val provider: LlmProvider = LlmProvider.NONE,
    /** Clé API (vide = désactivé sauf Ollama). Stockée en 0600 dans ~/.codeshield/config.json */
    val apiKey: String = "",
    val model: String = "",
    /** Uniquement pour CUSTOM / OLLAMA (ex. http://localhost:11434) */
    val baseUrl: String = "",
    val enabled: Boolean = false,
    val theme: AppTheme = AppTheme.DARK,
    val language: codeshield.ui.i18n.AppLanguage = codeshield.ui.i18n.AppLanguage.FR,
    /**
     * Budget de tokens de la réponse (max_tokens / maxOutputTokens).
     * Avant : 1200 en dur -> réponses coupées net en pleine phrase.
     */
    val maxTokens: Int = 4096,
    /** Projets consultés récents (chemins absolus, max 10). */
    val recentProjects: List<String> = emptyList(),
    /**
     * Claude Code exécute ses commandes (ex. `bun install`) sans demander la permission.
     * Désactivé par défaut : chaque commande est validée dans l'app.
     */
    val claudeAutoApprove: Boolean = false,
    /** Agent de code qui corrige les failles (CLI installé en local). */
    val codingAgent: codeshield.agent.CodingAgentKind = codeshield.agent.CodingAgentKind.CLAUDE_CODE,
    /** Largeur du panneau de détail des failles (dp), 0 = automatique ; ajustée à la souris. */
    val detailPaneWidth: Int = 0,
    /** Largeur du panneau des groupes (dp), 0 = défaut. */
    val groupsPaneWidth: Int = 0,
) {
    fun redacted(): String = if (apiKey.isBlank()) "(vide)" else "•••${apiKey.takeLast(4)}"

    /** Borne de sécurité : évite 0 (refus API) ou des valeurs absurdement hautes. */
    fun coercedMaxTokens(): Int = maxTokens.coerceIn(256, 32000)
}

object LlmModels {
    fun defaultsFor(provider: LlmProvider): List<String> = when (provider) {
        LlmProvider.NONE -> emptyList()
        LlmProvider.OPENAI -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "o4-mini")
        LlmProvider.ANTHROPIC -> listOf("claude-sonnet-4-20250514", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest")
        LlmProvider.GEMINI -> listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
        LlmProvider.MISTRAL -> listOf("mistral-large-latest", "mistral-small-latest", "codestral-latest")
        LlmProvider.OLLAMA -> listOf("llama3.1", "qwen2.5-coder", "codestral", "mistral")
        LlmProvider.OPENROUTER -> listOf("anthropic/claude-sonnet-4", "google/gemini-2.0-flash-001", "openai/gpt-4o-mini")
        LlmProvider.CUSTOM -> listOf("gpt-4o-mini")
    }

    fun defaultModel(provider: LlmProvider): String = defaultsFor(provider).firstOrNull().orEmpty()

    fun defaultBaseUrl(provider: LlmProvider): String = when (provider) {
        LlmProvider.OPENAI -> "https://api.openai.com/v1"
        LlmProvider.ANTHROPIC -> "https://api.anthropic.com"
        LlmProvider.GEMINI -> "https://generativelanguage.googleapis.com"
        LlmProvider.MISTRAL -> "https://api.mistral.ai/v1"
        LlmProvider.OLLAMA -> "http://localhost:11434"
        LlmProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
        LlmProvider.CUSTOM -> ""
        LlmProvider.NONE -> ""
    }

    fun needsKey(provider: LlmProvider): Boolean = when (provider) {
        LlmProvider.NONE, LlmProvider.OLLAMA -> false
        else -> true
    }
}

/** Construit le prompt metadata-only (JAMAIS de code source, cf. Sécurité Intellectuelle). */
fun buildTriagePrompt(
    projectDir: String,
    findings: List<codeshield.model.Finding>,
    maxFindings: Int = 30,
): String {
    val head = findings.take(maxFindings).joinToString("\n") {
        "- [${it.severity}] ${it.tool}/${it.ruleId} @ ${it.filePath}:${it.lineNumber} — ${it.title} | ${it.message?.take(300).orEmpty()}"
    }
    return """
        Tu es un expert AppSec. Voici des METADONNEES de scan (pas de code source).
        Projet: $projectDir
        Findings (${findings.size} au total, top $maxFindings affichés):
        $head
        Réponds en français, concis et actionnable:
        1. Top 3 priorités (pourquoi critiques)
        2. Faux positifs probables
        3. Prochaines actions (commandes, config, fix)
    """.trimIndent()
}

fun buildExplainPrompt(finding: codeshield.model.Finding, local: codeshield.logic.IssueExplanation): String = """
    Tu es un expert AppSec. Explique cette vulnérabilité (métadonnées uniquement, pas de code).
    Outil: ${finding.tool} | Règle: ${finding.ruleId} | Fichier: ${finding.filePath}:${finding.lineNumber}
    Sévérité: ${finding.severity} | Titre: ${finding.title}
    Message outil: ${finding.message?.take(600).orEmpty()}
    Base locale: catégorie=${local.category} | ${local.what}
    Réponds en français: 1) Ce que c'est 2) Pourquoi c'est grave ici 3) Comment corriger (étapes concrètes) 4) Refs OWASP. Max 25 lignes.
""".trimIndent()
