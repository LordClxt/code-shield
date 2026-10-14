package org.example.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import codeshield.tools.ToolState
import codeshield.tools.ToolStatus
import codeshield.ui.ConsultedProject
import codeshield.ui.EngineRun
import codeshield.ui.EngineState
import codeshield.ui.GroupTab
import codeshield.ui.ScannerUiState
import codeshield.ui.WorkspaceTabState
import codeshield.ui.components.AppSection
import codeshield.ui.components.FindingsSection
import codeshield.ui.components.OverviewSection
import codeshield.ui.components.SidePanel
import codeshield.ui.components.Sidebar
import codeshield.ui.design.VDivider
import codeshield.ui.design.WorkspaceTabBar
import codeshield.ui.groupFindings
import codeshield.ui.i18n.AppLanguage
import codeshield.ui.i18n.LocalStrings
import codeshield.ui.i18n.stringsFor
import codeshield.ui.theme.Shield
import codeshield.ui.theme.ShieldTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Galerie de captures des écrans principaux, rendue hors écran (Skia) à partir
 * de données d'exemple. Outil de revue visuelle, pas un test de non-régression :
 * ne s'exécute que si SNAPSHOTS_DIR est défini.
 *
 *   SNAPSHOTS_DIR=/tmp/shots ./gradlew :desktopApp:test --tests '*SnapshotGallery*'
 */
class SnapshotGallery {
    private val out = System.getenv("SNAPSHOTS_DIR").orEmpty()

    private val findings = listOf(
        Finding(ToolName.SEMGREP, "python.flask.security.injection.tainted-sql-string", "app/routes/users.py", 10,
            Severity.CRITICAL, "SQL injection via f-string", column = 23, endColumn = 61, cwe = listOf("CWE-89")),
        Finding(ToolName.GITLEAKS, "stripe-access-token", "app/config.py", 4, Severity.HIGH, "Stripe live secret key",
            column = 13, endColumn = 40, cwe = listOf("CWE-798")),
        Finding(ToolName.TRIVY, "CVE-2023-30861", "requirements.txt", 3, Severity.HIGH, "Flask session cookie disclosure",
            packageName = "flask", installedVersion = "2.2.2", fixedVersion = "2.2.5"),
        Finding(ToolName.SEMGREP, "python.flask.security.audit.debug-enabled", "app/main.py", 42, Severity.MEDIUM,
            "Flask app run with debug=True"),
        Finding(ToolName.TRIVY, "DS002", "Dockerfile", 1, Severity.MEDIUM, "Container runs as root"),
        Finding(ToolName.SEMGREP, "python.lang.security.audit.md5-used", "app/auth/hash.py", 7, Severity.LOW,
            "Weak hash algorithm (MD5)", cwe = listOf("CWE-327")),
        Finding(ToolName.OSV_SCANNER, "GHSA-xxxx", "requirements.txt", 5, Severity.LOW, "Jinja2 sandbox escape"),
    )

    private val code = """
        from flask import Flask, request, jsonify
        from app.db import db

        API_TOKEN = "sk_live_51H••••••••••••••••"

        app = Flask(__name__)

        @app.get("/users/search")
        def search_users():
            rows = db.execute(f"SELECT * FROM users WHERE name='{request.args['q']}'")
            # TODO: limiter les champs
            return jsonify(rows)
    """.trimIndent().lines()

    private val state = ScannerUiState(
        tabs = listOf(
            WorkspaceTabState(id = "tab-1", projectDir = "/Users/dev/shop-api", findings = findings),
            WorkspaceTabState(id = "tab-2", projectDir = "/Users/dev/billing", isScanning = true),
        ),
        activeTabId = "tab-1",
        projectDir = "/Users/dev/shop-api",
        findings = findings,
        selected = findings[0],
        fileLines = code,
        toolStatuses = ToolName.entries.associateWith {
            ToolStatus(it, if (it == ToolName.DEPENDENCY_CHECK) ToolState.MISSING else ToolState.READY)
        },
        engineRuns = mapOf(
            ToolName.SEMGREP to EngineRun(ToolName.SEMGREP, EngineState.DONE, findings = 2, durationMs = 8_400),
            ToolName.GITLEAKS to EngineRun(ToolName.GITLEAKS, EngineState.DONE, findings = 1, durationMs = 1_200),
            ToolName.TRIVY to EngineRun(ToolName.TRIVY, EngineState.DONE, findings = 1, durationMs = 12_900),
        ),
        terminal = listOf("[trivy] 1 finding in 12.9s"),
    )

    @Test
    fun render() {
        if (out.isBlank()) return
        File(out).mkdirs()
        shot("overview") { Shell(AppSection.OVERVIEW) }
        shot("findings") { Shell(AppSection.FINDINGS, withSelection = false) }
        shot("findings-detail") { Shell(AppSection.FINDINGS) }
        // En-tête adaptatif : fenêtre minimale (1100) et intermédiaire (1280), groupe sélectionné.
        shot("header-1100", width = 1100, height = 300) { Shell(AppSection.FINDINGS, withSelection = false, group = "frontend/src/app/core/services") }
        shot("header-1280", width = 1280, height = 300) { Shell(AppSection.FINDINGS, withSelection = false, group = "(root)") }
        // Agent : Claude Code attend l'accord pour une commande.
        shot("agent-permission") {
            Shell(
                AppSection.FINDINGS,
                agentFix = codeshield.ui.ClaudeFixState(
                    findings = listOf(findings[0]),
                    log = listOf("→ Read app/routes/users.py", "→ Edit app/routes/users.py", "Requête paramétrée en place ; je régénère le lockfile."),
                    pendingPermissions = listOf(codeshield.ui.ClaudePermission("r1", "Bash", "bun install", "Regenerate the lockfile with the new override")),
                ),
            )
        }
        // Agent : OpenCode a fini, une commande refusée d'office attend l'utilisateur.
        shot("agent-blocked") {
            Shell(
                AppSection.FINDINGS,
                agentKind = codeshield.agent.CodingAgentKind.OPENCODE,
                agentFix = codeshield.ui.ClaudeFixState(
                    findings = listOf(findings[0]),
                    agent = codeshield.agent.CodingAgentKind.OPENCODE,
                    running = false, success = true,
                    summary = "Override `piscina: ^5.3.2` ajouté dans package.json. Lancez `bun install` pour mettre à jour le lockfile.",
                    log = listOf("→ Edit package.json", "⏸ Bloquée : bun install"),
                    blockedCommands = listOf(codeshield.ui.BlockedCommand(1, "bun install", "Permission refusée d'office par OpenCode.")),
                ),
            )
        }
        // Téléchargements : un moteur en cours (60 %), un en vérification (indéterminé).
        shot("overview-installing") {
            Shell(AppSection.OVERVIEW, override = state.copy(toolStatuses = state.toolStatuses +
                (ToolName.TRIVY to ToolStatus(ToolName.TRIVY, ToolState.INSTALLING, progress = 0.6f)) +
                (ToolName.OSV_SCANNER to ToolStatus(ToolName.OSV_SCANNER, ToolState.INSTALLING, progress = -1f))))
        }
    }

    @Composable
    private fun Shell(
        section: AppSection,
        withSelection: Boolean = true,
        override: ScannerUiState? = null,
        agentFix: codeshield.ui.ClaudeFixState? = null,
        agentKind: codeshield.agent.CodingAgentKind = codeshield.agent.CodingAgentKind.CLAUDE_CODE,
        group: String? = null,
    ) {
        val base = override ?: state
        val s = if (withSelection) base else base.copy(selected = null)
        Row(Modifier.fillMaxSize().background(Shield.Bg)) {
            Sidebar(
                active = section, onSelect = {}, projectDir = s.projectDir,
                consultedProjects = listOf(ConsultedProject(s.projectDir, findingsCount = findings.size)),
                findingsCount = findings.size, isScanning = false, backendOnline = true,
                enginesReady = 6, enginesTotal = 7, onToggleLanguage = {},
            )
            VDivider()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                WorkspaceTabBar(s.tabs, s.activeTabId, {}, {}, {})
                Row(Modifier.fillMaxSize()) {
                    val groups = groupFindings(findings, GroupTab.FOLDERS)
                    when (section) {
                        AppSection.OVERVIEW -> OverviewSection(s, dirValid = true, onDirChange = {}, onBrowse = {},
                            onToggleEngine = { _, _ -> }, onToggleAutoDetect = {}, onInstall = {}, onRun = {})
                        else -> {
                            SidePanel(GroupTab.FOLDERS, {}, groups, setOf(groups.first().key), {}, null, {},
                                s.selected, {}, {}, findings.size)
                            VDivider()
                            FindingsSection(
                                breadcrumb = group ?: "All findings", onClearFilter = {}, groups = groups, selectedGroup = group,
                                onFixCriticalAndHigh = {},
                                onSelectGroup = {}, visible = findings, selected = s.selected, fileLines = s.fileLines,
                                onSelect = {}, onCloseDiff = {}, allFindings = findings, onOpenInIde = {},
                                claudeFix = agentFix, onFixWithClaude = {},
                                agent = codeshield.ui.components.AgentControls(
                                    kind = agentKind,
                                    paths = mapOf(
                                        codeshield.agent.CodingAgentKind.CLAUDE_CODE to "/usr/local/bin/claude",
                                        codeshield.agent.CodingAgentKind.OPENCODE to "/usr/local/bin/opencode",
                                        codeshield.agent.CodingAgentKind.CODEX to null,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shot(name: String, width: Int = 1440, height: Int = 900, content: @Composable () -> Unit) {
        ImageComposeScene(width, height, Density(1f)) {
            CompositionLocalProvider(LocalStrings provides stringsFor(AppLanguage.FR)) {
                ShieldTheme { content() }
            }
        }.use { scene ->
            // Plusieurs images : les polices des ressources se chargent après la première passe,
            // et les animations d'entrée ont le temps d'arriver à leur état final.
            var image = scene.render(0)
            for (i in 1..40) image = scene.render(i * 50_000_000L)
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
            File(out, "$name.png").writeBytes(png.bytes)
        }
    }
}
