package codeshield.parser

import codeshield.model.Category
import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

// ---------------------------------------------------------------- utilitaires

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonElement?.strings(): List<String> = when (this) {
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
    is JsonPrimitive -> listOfNotNull(contentOrNull?.takeIf(String::isNotBlank))
    else -> emptyList()
}

private val cweRe = Regex("""CWE-(\d{1,6})""", RegexOption.IGNORE_CASE)

/** Extrait des identifiants CWE normalisés (`CWE-89`) depuis du texte libre ou des nombres. */
internal fun normalizeCwes(raw: List<String>): List<String> = raw.flatMap { s ->
    val matches = cweRe.findAll(s).map { "CWE-${it.groupValues[1]}" }.toList()
    matches.ifEmpty { listOfNotNull(s.trim().toIntOrNull()?.let { "CWE-$it" }) }
}.distinct().take(20)

/**
 * Chemin relatif à la racine scannée, séparateurs `/`. Les outils renvoient
 * selon les cas `./a.py`, `/abs/proj/a.py` ou `/a.tf` (checkov) : on unifie,
 * pour que le visualiseur relise le bon fichier et que l'identité backend
 * soit indépendante de la machine.
 */
fun normalizeFindingPath(raw: String, projectDir: String = ""): String {
    var p = raw.replace('\\', '/')
    val root = projectDir.replace('\\', '/').trimEnd('/')
    if (root.isNotEmpty() && p.startsWith("$root/")) p = p.removePrefix("$root/")
    while (p.startsWith("./")) p = p.removePrefix("./")
    return p.ifBlank { raw }
}

internal fun mapCommonSeverity(raw: String?): Severity = when (raw?.uppercase()) {
    "CRITICAL" -> Severity.CRITICAL
    "HIGH", "ERROR" -> Severity.HIGH
    "MEDIUM", "MODERATE", "WARNING" -> Severity.MEDIUM
    else -> Severity.LOW
}

/** Score CVSS -> sévérité (barème NVD v3). */
internal fun severityFromCvss(score: Double?): Severity? = when {
    score == null -> null
    score >= 9.0 -> Severity.CRITICAL
    score >= 7.0 -> Severity.HIGH
    score >= 4.0 -> Severity.MEDIUM
    else -> Severity.LOW
}

// ---------------------------------------------------------------- semgrep

/** Mappe une sévérité brute semgrep vers le contrat Severity (défaut MEDIUM). */
fun mapSemgrepSeverity(raw: String?): Severity = when (raw?.uppercase()) {
    "ERROR", "CRITICAL" -> Severity.CRITICAL
    "WARNING", "HIGH" -> Severity.HIGH
    "INFO", "LOW" -> Severity.LOW
    else -> Severity.MEDIUM
}

/**
 * Parse la sortie `semgrep --json` en findings (métadonnées uniquement).
 * Tolérant : entrées incomplètes ignorées, jamais d'exception propagée.
 * CWE/OWASP sont extraits en champs structurés ; `extra.lines` (code source)
 * n'est jamais lu — directive Sécurité Intellectuelle.
 */
fun parseSemgrepResults(rawJson: String, projectDir: String = ""): List<Finding> = runCatching {
    val root = lenient.parseToJsonElement(rawJson).jsonObject
    val results = root["results"]?.jsonArray ?: return@runCatching emptyList<Finding>()
    results.mapNotNull { element ->
        runCatching {
            val obj = element.jsonObject
            val path = obj.str("path") ?: return@runCatching null
            val start = obj.obj("start")
            val end = obj.obj("end")
            val line = start?.int("line") ?: 1
            val endLine = end?.int("line")?.takeIf { it > line }
            val checkId = obj.str("check_id") ?: "semgrep.unknown"
            val extra = obj.obj("extra")
            val meta = extra?.obj("metadata")
            val baseMsg = extra?.str("message").orEmpty()
            Finding(
                tool = ToolName.SEMGREP,
                ruleId = checkId,
                filePath = normalizeFindingPath(path, projectDir),
                lineNumber = line,
                endLine = endLine,
                column = start?.int("col")?.takeIf { it >= 1 },
                endColumn = end?.int("col")?.takeIf { it >= 1 },
                severity = mapSemgrepSeverity(extra?.str("severity")),
                title = checkId.substringAfterLast('.'),
                message = enrichSemgrepMessage(baseMsg, extra).take(2000).ifBlank { null },
                category = Category.SAST,
                cwe = normalizeCwes(meta?.get("cwe").strings()),
                owasp = meta?.get("owasp").strings().take(20),
            )
        }.getOrNull()
    }
}.getOrDefault(emptyList())

/** Concatène message + metadata utiles (jamais `lines`). */
internal fun enrichSemgrepMessage(base: String, extra: JsonObject?): String {
    if (extra == null) return base
    val meta = extra.obj("metadata") ?: return base
    val parts = mutableListOf<String>()
    stringField(meta, "category")?.let { parts.add("Catégorie: $it") }
    stringField(meta, "confidence")?.let { parts.add("Confiance: $it") }
    stringField(meta, "impact")?.let { parts.add("Impact: $it") }
    stringField(meta, "likelihood")?.let { parts.add("Probabilité: $it") }
    // `fix` parfois présent dans metadata (guidance, pas de code utilisateur).
    stringField(meta, "fix")?.take(500)?.let { parts.add("Piste de fix: $it") }
    listField(meta, "references")?.take(500)?.let { parts.add("Refs: $it") }
    if (parts.isEmpty()) return base
    return if (base.isBlank()) parts.joinToString(" | ") else "$base\n${parts.joinToString(" | ")}"
}

internal fun stringField(obj: JsonObject, key: String): String? = obj.str(key)

internal fun listField(obj: JsonObject, key: String): String? =
    obj[key].strings().joinToString(", ").takeIf { it.isNotBlank() }

// ---------------------------------------------------------------- gitleaks

/**
 * Parse le rapport gitleaks (lancé avec `--redact`). Sévérité HIGH : secret exposé.
 * `Secret` et `Match` ne sont JAMAIS lus (valeurs sensibles).
 */
fun parseGitleaksResults(rawJson: String, projectDir: String = ""): List<Finding> = runCatching {
    val array = lenient.parseToJsonElement(rawJson).jsonArray
    array.mapNotNull { element ->
        runCatching {
            val obj = element.jsonObject
            val file = obj.str("File") ?: return@runCatching null
            val line = obj.int("StartLine")?.takeIf { it >= 1 } ?: 1
            val endLine = obj.int("EndLine")?.takeIf { it > line }
            val rule = obj.str("RuleID") ?: "gitleaks.unknown"
            val desc = obj.str("Description").orEmpty()
            val tags = obj["Tags"].strings().joinToString(", ")
            val enriched = buildString {
                append(desc.ifBlank { "Credential potentiellement exposée ($rule)" })
                if (tags.isNotBlank()) append("\nTags: $tags")
                append("\nAction: révoquer le secret, purger l'historique git, externaliser (env/vault).")
            }.take(2000)
            Finding(
                tool = ToolName.GITLEAKS,
                ruleId = rule,
                filePath = normalizeFindingPath(file, projectDir),
                lineNumber = line,
                endLine = endLine,
                column = obj.int("StartColumn")?.takeIf { it >= 1 },
                endColumn = obj.int("EndColumn")?.takeIf { it >= 1 },
                severity = Severity.HIGH,
                title = "Secret exposed: $rule",
                message = enriched,
                category = Category.SECRET,
                cwe = listOf("CWE-798"),
            )
        }.getOrNull()
    }
}.getOrDefault(emptyList())
