package codeshield.scanner

import codeshield.model.Category
import codeshield.model.Finding
import codeshield.model.ToolName
import codeshield.parser.parseBanditResults
import codeshield.parser.parseCheckovResults
import codeshield.parser.parseDependencyCheckResults
import codeshield.parser.parseGitleaksResults
import codeshield.parser.parseOsvResults
import codeshield.parser.parseSemgrepResults
import codeshield.parser.parseTrivyResults

/** Où le moteur écrit son JSON. */
enum class OutputMode { STDOUT, REPORT_FILE }

/**
 * Un moteur de scan. Les moteurs s'exécutent avec `cwd = racine du projet`
 * et ciblent `.` : les chemins remontés sont relatifs au projet.
 * Les rapports sont écrits dans un dossier temporaire, jamais dans le dépôt.
 */
interface ScannerEngine {
    val tool: ToolName
    val label: String
    /** Binaire système recherché dans le PATH. */
    val binary: String
    val categories: Set<Category>
    val description: String
    /** Commande d'installation affichée quand le binaire manque. */
    val installHint: String
    val defaultEnabled: Boolean
    val outputMode: OutputMode
    val timeoutMs: Long get() = 15 * 60_000L

    /** Le moteur a-t-il quelque chose à analyser dans ce projet ? */
    fun isRelevant(profile: ProjectProfile): Boolean = true

    /** Chemin du rapport JSON dans `workDir` (si [OutputMode.REPORT_FILE]). */
    fun reportPath(workDir: String): String = "$workDir/${tool.name.lowercase()}.json"

    fun command(binaryPath: String, workDir: String, options: EngineOptions): List<String>

    /** Exit codes « résultats trouvés » ≠ erreur. */
    fun isExpectedExit(code: Int): Boolean = code == 0 || code == 1

    fun parse(rawJson: String, projectDir: String): List<Finding>
}

/**
 * Ruleset Semgrep par défaut. `--config auto` exige l'envoi de métriques à Semgrep,
 * incompatible avec `--metrics=off` (code 2) : on utilise le ruleset du registre.
 */
const val DEFAULT_SEMGREP_CONFIG = "p/default"

data class EngineOptions(val semgrepConfig: String = DEFAULT_SEMGREP_CONFIG)

/**
 * Indices sur le contenu du projet (détection automatique des moteurs utiles).
 * [UNKNOWN] = profil non calculé : tous les moteurs sont jugés pertinents.
 */
data class ProjectProfile(
    val extensions: Set<String> = emptySet(),
    val fileNames: Set<String> = emptySet(),
    val known: Boolean = true,
) {
    val hasPython: Boolean get() = !known || "py" in extensions
    val hasIac: Boolean
        get() = !known || extensions.any { it in setOf("tf", "hcl", "bicep") } ||
            fileNames.any { it == "dockerfile" || it.endsWith(".dockerfile") || it == "chart.yaml" || it == "kustomization.yaml" } ||
            fileNames.any { it.endsWith(".yml") || it.endsWith(".yaml") }
    val hasManifests: Boolean get() = !known || fileNames.any { it in MANIFESTS }

    companion object {
        val UNKNOWN = ProjectProfile(known = false)
        val MANIFESTS = setOf(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "bun.lock", "requirements.txt", "poetry.lock",
            "pipfile.lock", "uv.lock", "go.sum", "cargo.lock", "gemfile.lock", "composer.lock", "pom.xml",
            "build.gradle", "build.gradle.kts", "gradle.lockfile", "packages.lock.json", "pubspec.lock", "mix.lock",
        )
    }
}

object SemgrepEngine : ScannerEngine {
    override val tool = ToolName.SEMGREP
    override val label = "Semgrep"
    override val binary = "semgrep"
    override val categories = setOf(Category.SAST)
    override val description = "SAST multi-langages (injections, XSS, crypto…)"
    override val installHint = "`brew install semgrep` (ou `pip install semgrep`)"
    override val defaultEnabled = true
    override val outputMode = OutputMode.STDOUT
    override fun command(binaryPath: String, workDir: String, options: EngineOptions) = listOf(
        binaryPath, "scan", "--config", options.semgrepConfig.takeUnless { it == "auto" } ?: DEFAULT_SEMGREP_CONFIG,
        "--json", "--quiet", "--metrics=off", ".",
    )
    override fun parse(rawJson: String, projectDir: String) = parseSemgrepResults(rawJson, projectDir)
}

object GitleaksEngine : ScannerEngine {
    override val tool = ToolName.GITLEAKS
    override val label = "Gitleaks"
    override val binary = "gitleaks"
    override val categories = setOf(Category.SECRET)
    override val description = "Secrets commités (clés API, tokens, clés privées)"
    override val installHint = "`brew install gitleaks`"
    override val defaultEnabled = true
    override val outputMode = OutputMode.REPORT_FILE
    // --redact : le secret n'apparaît ni dans le rapport ni dans le terminal.
    override fun command(binaryPath: String, workDir: String, options: EngineOptions) = listOf(
        binaryPath, "detect", "--source", ".", "--report-format", "json",
        "--report-path", reportPath(workDir), "--no-git", "--redact", "--no-banner",
    )
    override fun parse(rawJson: String, projectDir: String) = parseGitleaksResults(rawJson, projectDir)
}

object TrivyEngine : ScannerEngine {
    override val tool = ToolName.TRIVY
    override val label = "Trivy"
    override val binary = "trivy"
    override val categories = setOf(Category.SCA, Category.IAC, Category.SECRET)
    override val description = "Dépendances vulnérables, IaC (Docker, K8s, Terraform) et secrets"
    override val installHint = "`brew install trivy`"
    override val defaultEnabled = true
    override val outputMode = OutputMode.REPORT_FILE
    override fun command(binaryPath: String, workDir: String, options: EngineOptions) = listOf(
        binaryPath, "fs", "--scanners", "vuln,misconfig,secret", "--format", "json",
        "--output", reportPath(workDir), "--quiet", "--skip-dirs", "node_modules", ".",
    )
    // --exit-code non fourni : 0 même avec findings.
    override fun parse(rawJson: String, projectDir: String) = parseTrivyResults(rawJson, projectDir)
}

object OsvScannerEngine : ScannerEngine {
    override val tool = ToolName.OSV_SCANNER
    override val label = "OSV-Scanner"
    override val binary = "osv-scanner"
    override val categories = setOf(Category.SCA)
    override val description = "Dépendances vulnérables (base OSV.dev, tous écosystèmes)"
    override val installHint = "`brew install osv-scanner`"
    override val defaultEnabled = false
    override val outputMode = OutputMode.STDOUT
    override fun isRelevant(profile: ProjectProfile) = profile.hasManifests
    override fun command(binaryPath: String, workDir: String, options: EngineOptions) = listOf(
        binaryPath, "scan", "source", "--recursive", "--format", "json", ".",
    )
    // 1 = vulnérabilités trouvées, 128 = aucun manifeste trouvé.
    override fun isExpectedExit(code: Int) = code == 0 || code == 1 || code == 128
    override fun parse(rawJson: String, projectDir: String) = parseOsvResults(rawJson, projectDir)
}

object BanditEngine : ScannerEngine {
    override val tool = ToolName.BANDIT
    override val label = "Bandit"
    override val binary = "bandit"
    override val categories = setOf(Category.SAST)
    override val description = "SAST Python dédié (subprocess, pickle, crypto…)"
    override val installHint = "`pipx install bandit` (ou `brew install bandit`)"
    override val defaultEnabled = true
    override val outputMode = OutputMode.REPORT_FILE
    override fun isRelevant(profile: ProjectProfile) = profile.hasPython
    override fun command(binaryPath: String, workDir: String, options: EngineOptions) = listOf(
        binaryPath, "-r", ".", "-f", "json", "-o", reportPath(workDir), "-q",
        "-x", "./.venv,./venv,./node_modules,./build,./dist,./.git",
    )
    override fun parse(rawJson: String, projectDir: String) = parseBanditResults(rawJson, projectDir)
}

object CheckovEngine : ScannerEngine {
    override val tool = ToolName.CHECKOV
    override val label = "Checkov"
    override val binary = "checkov"
    override val categories = setOf(Category.IAC)
    override val description = "IaC : Terraform, Kubernetes, Dockerfile, GitHub Actions"
    override val installHint = "`pipx install checkov` (ou `brew install checkov`)"
    override val defaultEnabled = true
    override val outputMode = OutputMode.STDOUT
    override fun isRelevant(profile: ProjectProfile) = profile.hasIac
    override fun command(binaryPath: String, workDir: String, options: EngineOptions) = listOf(
        binaryPath, "-d", ".", "-o", "json", "--quiet", "--compact", "--skip-download",
        "--skip-path", "node_modules",
    )
    override fun parse(rawJson: String, projectDir: String) = parseCheckovResults(rawJson, projectDir)
}

object DependencyCheckEngine : ScannerEngine {
    override val tool = ToolName.DEPENDENCY_CHECK
    override val label = "Dependency-Check"
    override val binary = "dependency-check.sh"
    override val categories = setOf(Category.SCA)
    override val description = "SCA OWASP (JVM, base NVD locale — lent au premier lancement)"
    override val installHint = "`brew install dependency-check`"
    override val defaultEnabled = false
    override val outputMode = OutputMode.REPORT_FILE
    override val timeoutMs = 45 * 60_000L
    override fun isRelevant(profile: ProjectProfile) = profile.hasManifests
    override fun reportPath(workDir: String) = "$workDir/dependency-check-report.json"
    override fun command(binaryPath: String, workDir: String, options: EngineOptions) = listOf(
        binaryPath, "--scan", ".", "--format", "JSON", "--out", workDir, "--project", "codeshield",
    )
    override fun isExpectedExit(code: Int) = code == 0
    override fun parse(rawJson: String, projectDir: String) = parseDependencyCheckResults(rawJson, projectDir)
}

/** Registre des moteurs, dans l'ordre d'affichage. */
object ScannerEngines {
    val all: List<ScannerEngine> = listOf(
        SemgrepEngine, GitleaksEngine, TrivyEngine, BanditEngine, CheckovEngine, OsvScannerEngine, DependencyCheckEngine,
    )

    fun of(tool: ToolName): ScannerEngine = all.first { it.tool == tool }

    val defaultEnabled: Set<ToolName> = all.filter { it.defaultEnabled }.map { it.tool }.toSet()
}
