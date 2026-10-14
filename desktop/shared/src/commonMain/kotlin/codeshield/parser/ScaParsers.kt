package codeshield.parser

import codeshield.model.Category
import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

// Parsers des moteurs de dépendances (SCA) et de Trivy (SCA + secrets + IaC).
// Métadonnées uniquement : les champs contenant du code (`Code`, `Match`,
// `Secret`, `Lines`…) ne sont jamais lus.

/**
 * Trivy `fs --format json`. Une cible (`Target`) = un fichier ; trois familles :
 * `Vulnerabilities` (SCA), `Secrets`, `Misconfigurations` (IaC).
 */
fun parseTrivyResults(rawJson: String, projectDir: String = ""): List<Finding> = runCatching {
    val root = lenient.parseToJsonElement(rawJson).jsonObject
    val results = root.arr("Results") ?: return@runCatching emptyList<Finding>()
    results.flatMap { r ->
        val res = r as? JsonObject ?: return@flatMap emptyList()
        val target = normalizeFindingPath(res.str("Target") ?: return@flatMap emptyList(), projectDir)
        buildList {
            res.arr("Vulnerabilities")?.forEach { v ->
                runCatching { trivyVuln(v.jsonObject, target) }.getOrNull()?.let(::add)
            }
            res.arr("Secrets")?.forEach { s ->
                runCatching {
                    val o = s.jsonObject
                    val line = o.int("StartLine")?.takeIf { it >= 1 } ?: 1
                    Finding(
                        tool = ToolName.TRIVY, ruleId = o.str("RuleID") ?: "trivy.secret",
                        filePath = target, lineNumber = line,
                        endLine = o.int("EndLine")?.takeIf { it > line },
                        severity = mapCommonSeverity(o.str("Severity")),
                        title = o.str("Title") ?: "Secret exposed",
                        message = o.str("Category")?.let { "Type: $it" },
                        category = Category.SECRET, cwe = listOf("CWE-798"),
                    )
                }.getOrNull()?.let(::add)
            }
            res.arr("Misconfigurations")?.forEach { m ->
                runCatching {
                    val o = m.jsonObject
                    if (o.str("Status") == "PASS") return@runCatching null
                    val cause = o.obj("CauseMetadata")
                    val line = cause?.int("StartLine")?.takeIf { it >= 1 } ?: 1
                    Finding(
                        tool = ToolName.TRIVY, ruleId = o.str("AVDID") ?: o.str("ID") ?: "trivy.misconfig",
                        filePath = target, lineNumber = line,
                        endLine = cause?.int("EndLine")?.takeIf { it > line },
                        severity = mapCommonSeverity(o.str("Severity")),
                        title = o.str("Title") ?: o.str("ID") ?: "Misconfiguration",
                        message = listOfNotNull(o.str("Description"), o.str("Resolution")?.let { "Fix: $it" })
                            .joinToString("\n").take(2000).ifBlank { null },
                        category = Category.IAC,
                    )
                }.getOrNull()?.let(::add)
            }
        }
    }
}.getOrDefault(emptyList())

private fun trivyVuln(o: JsonObject, target: String): Finding {
    val id = o.str("VulnerabilityID") ?: "trivy.vuln"
    val pkg = o.str("PkgName")
    return Finding(
        tool = ToolName.TRIVY, ruleId = id, filePath = target, lineNumber = 1,
        severity = mapCommonSeverity(o.str("Severity")),
        title = o.str("Title") ?: listOfNotNull(pkg, id).joinToString(": "),
        message = o.str("Description")?.take(2000),
        category = Category.SCA,
        cwe = normalizeCwes(o["CweIDs"].strings()),
        packageName = pkg, installedVersion = o.str("InstalledVersion"),
        fixedVersion = o.str("FixedVersion")?.substringBefore(',')?.trim(),
    )
}

/**
 * OSV-Scanner `scan source --format json`. Une faille par groupe d'alias
 * (évite les doublons GHSA/CVE pour une même vulnérabilité).
 */
fun parseOsvResults(rawJson: String, projectDir: String = ""): List<Finding> = runCatching {
    val root = lenient.parseToJsonElement(rawJson).jsonObject
    val results = root.arr("results") ?: return@runCatching emptyList<Finding>()
    results.flatMap { r ->
        val res = r as? JsonObject ?: return@flatMap emptyList()
        val source = normalizeFindingPath(res.obj("source")?.str("path") ?: return@flatMap emptyList(), projectDir)
        res.arr("packages").orEmpty().flatMap { p ->
            val pkg = p as? JsonObject ?: return@flatMap emptyList()
            val info = pkg.obj("package")
            val name = info?.str("name")
            val version = info?.str("version")
            val vulns = pkg.arr("vulnerabilities").orEmpty().mapNotNull { it as? JsonObject }
                .associateBy { it.str("id") }
            val groups = pkg.arr("groups").orEmpty().mapNotNull { it as? JsonObject }
                .ifEmpty { vulns.keys.filterNotNull().map { JsonObject(mapOf("ids" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(it))))) } }
            groups.mapNotNull { g ->
                runCatching {
                    val ids = g["ids"].strings()
                    val primary = ids.firstOrNull { it.startsWith("CVE-") } ?: ids.first()
                    val v = ids.firstNotNullOfOrNull { vulns[it] }
                    val cvss = (g["max_severity"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                    val sev = severityFromCvss(cvss)
                        ?: v?.obj("database_specific")?.str("severity")?.let(::mapCommonSeverity)
                        ?: Severity.MEDIUM
                    Finding(
                        tool = ToolName.OSV_SCANNER, ruleId = primary, filePath = source, lineNumber = 1,
                        severity = sev,
                        title = v?.str("summary") ?: "$name: $primary",
                        message = ids.joinToString(", ").let { "Aliases: $it" },
                        category = Category.SCA,
                        cwe = normalizeCwes(v?.obj("database_specific")?.get("cwe_ids").strings()),
                        packageName = name, installedVersion = version,
                        fixedVersion = v?.let { osvFixedVersion(it, name) },
                    )
                }.getOrNull()
            }
        }
    }
}.getOrDefault(emptyList())

private fun osvFixedVersion(v: JsonObject, pkg: String?): String? =
    v.arr("affected").orEmpty().mapNotNull { it as? JsonObject }
        .filter { pkg == null || it.obj("package")?.str("name") == pkg }
        .flatMap { it.arr("ranges").orEmpty() }
        .mapNotNull { it as? JsonObject }
        .flatMap { it.arr("events").orEmpty() }
        .mapNotNull { (it as? JsonObject)?.str("fixed") }
        .firstOrNull()

/** OWASP Dependency-Check `--format JSON` (dependency-check-report.json). */
fun parseDependencyCheckResults(rawJson: String, projectDir: String = ""): List<Finding> = runCatching {
    val root = lenient.parseToJsonElement(rawJson).jsonObject
    root.arr("dependencies").orEmpty().flatMap { d ->
        val dep = d as? JsonObject ?: return@flatMap emptyList()
        val path = normalizeFindingPath(dep.str("filePath") ?: dep.str("fileName") ?: return@flatMap emptyList(), projectDir)
        val pkgId = dep.arr("packages")?.firstOrNull()?.let { (it as? JsonObject)?.str("id") }
        val name = pkgId?.substringAfterLast('/')?.substringBefore('@') ?: dep.str("fileName")
        val version = pkgId?.substringAfterLast('@', "")?.ifBlank { null }
        dep.arr("vulnerabilities").orEmpty().mapNotNull { v ->
            runCatching {
                val o = v.jsonObject
                val id = o.str("name") ?: return@runCatching null
                val score = (o.obj("cvssv3")?.get("baseScore") as? JsonPrimitive)?.doubleOrNull
                    ?: (o.obj("cvssv2")?.get("score") as? JsonPrimitive)?.doubleOrNull
                Finding(
                    tool = ToolName.DEPENDENCY_CHECK, ruleId = id, filePath = path, lineNumber = 1,
                    severity = severityFromCvss(score) ?: mapCommonSeverity(o.str("severity")),
                    title = "$name: $id",
                    message = o.str("description")?.take(2000),
                    category = Category.SCA,
                    cwe = normalizeCwes(o["cwes"].strings()),
                    packageName = name, installedVersion = version,
                )
            }.getOrNull()
        }
    }
}.getOrDefault(emptyList())
