package codeshield.parser

import codeshield.model.Category
import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Bandit `-f json`. `code` (extrait de source) n'est jamais lu.
 * HIGH + confiance HIGH reste HIGH : bandit n'a pas de niveau critique.
 */
fun parseBanditResults(rawJson: String, projectDir: String = ""): List<Finding> = runCatching {
    val root = lenient.parseToJsonElement(rawJson).jsonObject
    root.arr("results").orEmpty().mapNotNull { r ->
        runCatching {
            val o = r.jsonObject
            val file = o.str("filename") ?: return@runCatching null
            val line = o.int("line_number")?.takeIf { it >= 1 } ?: 1
            val range = o.arr("line_range")?.mapNotNull { it.jsonPrimitive.intOrNull }.orEmpty()
            val testId = o.str("test_id") ?: "bandit"
            val testName = o.str("test_name")
            val cwe = o.obj("issue_cwe")?.int("id")?.let { listOf("CWE-$it") }.orEmpty()
            Finding(
                tool = ToolName.BANDIT,
                ruleId = listOfNotNull(testId, testName).joinToString(":"),
                filePath = normalizeFindingPath(file, projectDir),
                lineNumber = line,
                endLine = range.maxOrNull()?.takeIf { it > line },
                column = o.int("col_offset")?.let { it + 1 },
                endColumn = o.int("end_col_offset")?.let { it + 1 },
                severity = mapCommonSeverity(o.str("issue_severity")),
                title = testName ?: testId,
                message = listOfNotNull(o.str("issue_text"), o.str("issue_confidence")?.let { "Confiance: $it" })
                    .joinToString("\n").take(2000).ifBlank { null },
                category = Category.SAST,
                cwe = cwe,
                remediationKey = testId,
            )
        }.getOrNull()
    }
}.getOrDefault(emptyList())

/**
 * Checkov `-o json` : un objet (un framework) ou une liste d'objets.
 * Les chemins sont relatifs à `-d` mais préfixés par `/` (`/main.tf`).
 */
fun parseCheckovResults(rawJson: String, projectDir: String = ""): List<Finding> = runCatching {
    val root = lenient.parseToJsonElement(rawJson)
    val reports: List<JsonElement> = when (root) {
        is JsonArray -> root
        is JsonObject -> listOf(root)
        else -> emptyList()
    }
    reports.flatMap { rep ->
        val failed = (rep as? JsonObject)?.obj("results")?.arr("failed_checks").orEmpty()
        failed.mapNotNull { c ->
            runCatching {
                val o = c.jsonObject
                val rawPath = o.str("file_path") ?: return@runCatching null
                // checkov préfixe par `/` des chemins relatifs à `-d`.
                val path = normalizeFindingPath(rawPath, projectDir).trimStart('/')
                val range = o.arr("file_line_range")?.mapNotNull { it.jsonPrimitive.intOrNull }.orEmpty()
                val line = range.firstOrNull()?.takeIf { it >= 1 } ?: 1
                val checkId = o.str("check_id") ?: "checkov"
                Finding(
                    tool = ToolName.CHECKOV,
                    ruleId = checkId,
                    filePath = path,
                    lineNumber = line,
                    endLine = range.getOrNull(1)?.takeIf { it > line },
                    severity = o.str("severity")?.let(::mapCommonSeverity) ?: Severity.MEDIUM,
                    title = o.str("check_name") ?: checkId,
                    message = listOfNotNull(o.str("resource")?.let { "Ressource: $it" }, o.str("guideline"))
                        .joinToString("\n").take(2000).ifBlank { null },
                    category = Category.IAC,
                    remediationKey = checkId,
                )
            }.getOrNull()
        }
    }
}.getOrDefault(emptyList())
