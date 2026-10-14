package org.example.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import codeshield.agent.CodingAgentKind
import codeshield.llm.AppTheme
import codeshield.ui.EngineState
import codeshield.ui.components.AppSection
import codeshield.ui.i18n.AppLanguage
import java.awt.Desktop
import java.awt.Taskbar
import java.net.URI

private val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)

/** ⌘ sur macOS, Ctrl ailleurs. */
private fun shortcut(key: Key, shift: Boolean = false) =
    KeyShortcut(key, meta = isMac, ctrl = !isMac, shift = shift)

private fun browse(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

private const val REPO_URL = "https://github.com/LordClxt/code-shield"

/** Libellé selon la langue de l'app. */
private fun AppCommands.t(fr: String, en: String) = if (state.llmSettings.language == AppLanguage.FR) fr else en

/** Barre de menus native : toutes les actions de l'app, avec leurs raccourcis. */
@Composable
fun FrameWindowScope.CodeShieldMenuBar(c: AppCommands) {
    val s = c.state
    val fix = s.claudeFix
    MenuBar {
        Menu(c.t("Fichier", "File"), mnemonic = 'F') {
            Item(c.t("Ouvrir un projet…", "Open Project…"), onClick = c.openProject, shortcut = shortcut(Key.O))
            Item(c.t("Nouvel onglet…", "New Tab…"), onClick = c.newTab, shortcut = shortcut(Key.T))
            Item(c.t("Fermer l'onglet", "Close Tab"), onClick = c.closeTab, shortcut = shortcut(Key.W))
            if (c.recentProjects.isNotEmpty()) {
                Separator()
                Menu(c.t("Projets récents", "Recent Projects")) {
                    for (path in c.recentProjects.take(10)) {
                        Item(path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\'), onClick = { c.openRecent(path) })
                    }
                }
            }
        }
        Menu("Scan", mnemonic = 'S') {
            Item(c.t("Lancer le scan", "Run Scan"), onClick = c.runScan, enabled = c.canScan, shortcut = shortcut(Key.R))
            Item(c.t("Arrêter le scan", "Stop Scan"), onClick = c.cancelScan, enabled = s.isScanning, shortcut = shortcut(Key.Period))
            Separator()
            Item(c.t("Rechercher une faille", "Find a Finding"), onClick = c.focusSearch, shortcut = shortcut(Key.K))
            Item(c.t("Synchroniser", "Sync"), onClick = c.sync, enabled = s.findings.isNotEmpty())
        }
        Menu("Agent", mnemonic = 'A') {
            val label = s.llmSettings.codingAgent.label
            Item(
                if (c.urgentCount > 0) c.t("Corriger ${c.urgentCount} failles critiques/hautes avec $label", "Fix ${c.urgentCount} critical/high findings with $label")
                else c.t("Aucune faille critique/haute", "No critical/high findings"),
                onClick = c.fixUrgent,
                enabled = c.urgentCount > 0 && fix?.running != true,
                shortcut = shortcut(Key.F, shift = true),
            )
            Item(c.t("Arrêter l'agent", "Stop Agent"), onClick = c.cancelFix, enabled = fix?.running == true)
            // Demande en attente : on peut répondre depuis le menu, sans chercher la carte.
            fix?.pendingPermissions?.firstOrNull()?.let { p ->
                Separator()
                Item(c.t("Autoriser : ${p.summary.take(60)}", "Allow: ${p.summary.take(60)}"), onClick = { c.respondPermission(p.requestId, true, false) }, shortcut = shortcut(Key.Enter))
                Item(c.t("Refuser", "Deny"), onClick = { c.respondPermission(p.requestId, false, false) })
            }
            Separator()
            for (kind in CodingAgentKind.entries) {
                val missing = s.agentPaths.isNotEmpty() && s.agentPaths[kind] == null
                RadioButtonItem(
                    kind.label + if (missing) c.t(" (non installé)", " (not installed)") else "",
                    selected = s.llmSettings.codingAgent == kind,
                    onClick = { c.setAgent(kind) },
                    enabled = fix?.running != true,
                )
            }
            Separator()
            CheckboxItem(
                c.t("Exécuter les commandes sans demander", "Run Commands Without Asking"),
                checked = s.llmSettings.claudeAutoApprove,
                onCheckedChange = c.setAutoApprove,
            )
        }
        Menu(c.t("Affichage", "View"), mnemonic = 'V') {
            val keys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)
            AppSection.entries.forEachIndexed { i, sec ->
                RadioButtonItem(
                    sectionLabel(c, sec),
                    selected = c.section == sec,
                    onClick = { c.goTo(sec) },
                    shortcut = keys.getOrNull(i)?.let { shortcut(it) },
                )
            }
            Separator()
            Menu(c.t("Thème", "Theme")) {
                for (theme in AppTheme.entries) {
                    RadioButtonItem(
                        if (theme == AppTheme.DARK) c.t("Sombre", "Dark") else c.t("Clair", "Light"),
                        selected = s.llmSettings.theme == theme,
                        onClick = { c.setTheme(theme) },
                    )
                }
            }
            Menu(c.t("Langue", "Language")) {
                RadioButtonItem("Français", selected = s.llmSettings.language == AppLanguage.FR, onClick = { c.setLanguage(AppLanguage.FR) })
                RadioButtonItem("English", selected = s.llmSettings.language == AppLanguage.EN, onClick = { c.setLanguage(AppLanguage.EN) })
            }
        }
        Menu(c.t("Aide", "Help"), mnemonic = 'H') {
            Item(c.t("Code source sur GitHub", "Source Code on GitHub"), onClick = { browse(REPO_URL) })
            Item(c.t("Signaler un problème", "Report an Issue"), onClick = { browse("$REPO_URL/issues/new") })
            Item(c.t("Notes de version", "Release Notes"), onClick = { browse("$REPO_URL/releases") })
            Separator()
            for (kind in CodingAgentKind.entries) {
                Item(c.t("Installer ${kind.label}", "Install ${kind.label}"), onClick = { browse(kind.installUrl) })
            }
        }
    }
}

private fun sectionLabel(c: AppCommands, sec: AppSection) = when (sec) {
    AppSection.OVERVIEW -> c.t("Vue d'ensemble", "Overview")
    AppSection.FINDINGS -> c.t("Vulnérabilités", "Findings")
    AppSection.PIPELINE -> "Pipeline"
    AppSection.PENTEST -> "Pentest"
    AppSection.HEALTH -> c.t("Synchronisation", "Sync")
    AppSection.SETTINGS -> c.t("Paramètres", "Settings")
}

/**
 * Dock / barre des tâches : pastille = actions en attente (permissions, commandes bloquées),
 * barre de progression pendant un scan, rebond quand l'agent attend une réponse ou a fini
 * pendant que la fenêtre est en arrière-plan. ⌘, ouvre les Paramètres.
 */
@Composable
fun DockIntegration(c: AppCommands, window: ComposeWindow, notify: (title: String, message: String, error: Boolean) -> Unit) {
    val taskbar = remember { if (Taskbar.isTaskbarSupported()) Taskbar.getTaskbar() else null }
    val s = c.state
    val fix = s.claudeFix
    val waiting = (fix?.pendingPermissions?.size ?: 0) + (fix?.blockedCommands?.count { it.exitCode == null && !it.running } ?: 0)

    // Pastille du Dock.
    LaunchedEffect(waiting) {
        if (taskbar?.isSupported(Taskbar.Feature.ICON_BADGE_TEXT) == true) {
            runCatching { taskbar.setIconBadge(if (waiting > 0) waiting.toString() else null) }
        }
    }

    // Progression du scan (moteurs terminés / lancés).
    val runs = s.engineRuns.values.filter { it.state != EngineState.SKIPPED && it.state != EngineState.MISSING }
    val done = runs.count { it.state in setOf(EngineState.DONE, EngineState.FAILED, EngineState.TIMEOUT, EngineState.CANCELLED) }
    val progress = if (s.isScanning && runs.isNotEmpty()) (done * 100 / runs.size).coerceIn(1, 99) else -1
    LaunchedEffect(progress) {
        if (taskbar?.isSupported(Taskbar.Feature.PROGRESS_VALUE) == true) runCatching { taskbar.setProgressValue(progress) }
    }

    // Notifications : seulement quand la fenêtre n'est pas au premier plan.
    fun attention(title: String, message: String, error: Boolean = false) {
        if (window.isActive) return
        notify(title, message, error)
        if (taskbar?.isSupported(Taskbar.Feature.USER_ATTENTION) == true) runCatching { taskbar.requestUserAttention(true, false) }
    }

    val pendingCount = fix?.pendingPermissions?.size ?: 0
    var lastPending by remember { mutableStateOf(0) }
    LaunchedEffect(pendingCount) {
        val p = fix?.pendingPermissions?.lastOrNull()
        if (pendingCount > lastPending && p != null) {
            attention(c.t("${fix.agent.label} attend votre accord", "${fix.agent.label} needs your approval"), p.summary)
        }
        lastPending = pendingCount
    }

    var wasFixing by remember { mutableStateOf(false) }
    LaunchedEffect(fix?.running) {
        val running = fix?.running == true
        if (wasFixing && !running && fix != null) {
            if (fix.success == true) {
                attention(c.t("${fix.agent.label} a terminé", "${fix.agent.label} finished"), c.t("Relancez le scan pour confirmer la correction.", "Re-scan to confirm the fix."))
            } else {
                attention(c.t("${fix.agent.label} s'est arrêté", "${fix.agent.label} stopped"), fix.summary?.lineSequence()?.firstOrNull().orEmpty(), error = true)
            }
        }
        wasFixing = running
    }

    var wasScanning by remember { mutableStateOf(false) }
    LaunchedEffect(s.isScanning) {
        if (wasScanning && !s.isScanning) {
            val critical = s.findings.count { it.severity == codeshield.model.Severity.CRITICAL }
            attention(
                c.t("Scan terminé", "Scan finished"),
                c.t("${s.findings.size} failles, dont $critical critiques.", "${s.findings.size} findings, $critical critical."),
            )
        }
        wasScanning = s.isScanning
    }

    // ⌘, (menu de l'app macOS) -> Paramètres.
    LaunchedEffect(Unit) {
        runCatching {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) desktop.setPreferencesHandler { c.goTo(AppSection.SETTINGS) }
        }
    }
}

/**
 * Icône dans la barre de menus (macOS) / zone de notification : état d'un coup d'œil
 * et actions rapides, même fenêtre fermée en arrière-plan.
 */
@Composable
fun ApplicationScope.CodeShieldTray(icon: Painter, trayState: TrayState, commands: MutableState<AppCommands?>, onShow: () -> Unit) {
    val c = commands.value
    val s = c?.state
    val fix = s?.claudeFix
    val status = when {
        s == null -> "CodeShield"
        fix?.pendingPermissions?.isNotEmpty() == true -> c.t("${fix.agent.label} attend votre accord", "${fix.agent.label} needs your approval")
        fix?.running == true -> c.t("${fix.agent.label} corrige…", "${fix.agent.label} is fixing…")
        s.isScanning -> c.t("Scan en cours…", "Scanning…")
        else -> c.t("${s.findings.size} failles", "${s.findings.size} findings")
    }
    Tray(
        icon = icon,
        state = trayState,
        tooltip = "CodeShield — $status",
        onAction = onShow,
        menu = {
            Item(status, enabled = false, onClick = {})
            Separator()
            Item(c?.t("Afficher CodeShield", "Show CodeShield") ?: "Show CodeShield", onClick = onShow)
            if (c != null) {
                fix?.pendingPermissions?.firstOrNull()?.let { p ->
                    Item(c.t("Autoriser : ${p.summary.take(50)}", "Allow: ${p.summary.take(50)}"), onClick = { c.respondPermission(p.requestId, true, false) })
                    Item(c.t("Refuser", "Deny"), onClick = { c.respondPermission(p.requestId, false, false) })
                }
                Item(c.t("Lancer le scan", "Run Scan"), enabled = c.canScan, onClick = { onShow(); c.runScan() })
                if (c.urgentCount > 0 && fix?.running != true) {
                    Item(c.t("Corriger ${c.urgentCount} critiques/hautes", "Fix ${c.urgentCount} critical/high"), onClick = { onShow(); c.fixUrgent() })
                }
            }
            Separator()
            Item(c?.t("Quitter", "Quit") ?: "Quit", onClick = ::exitApplication)
        },
    )
}

/** Envoie une notification système via l'icône de la barre de menus. */
fun TrayState.notify(title: String, message: String, error: Boolean) =
    sendNotification(Notification(title, message, if (error) Notification.Type.Error else Notification.Type.Info))
