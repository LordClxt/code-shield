package org.example.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import codeshield.ui.BinaryLocator
import codeshield.ui.CommandRunner
import codeshield.ui.DirChecker
import codeshield.ui.FileReader
import codeshield.cache.ScanHistoryStore
import codeshield.cache.InMemoryHistoryStore
import codeshield.llm.InMemorySettingsStore
import codeshield.llm.NoopLlmClient
import codeshield.llm.LlmClient
import codeshield.llm.SettingsStore
import codeshield.tools.ToolInstaller
import codeshield.tools.ToolState
import codeshield.ui.FindingGroup
import codeshield.ui.GroupTab
import codeshield.ui.ScannerViewModel
import codeshield.ui.SyncClientFactory
import codeshield.ui.components.AppSection
import codeshield.ui.components.FindingsSection
import codeshield.ui.components.OverviewSection
import codeshield.ui.components.PipelineSection
import codeshield.ui.components.SettingsSection
import codeshield.ui.components.SidePanel
import codeshield.ui.components.Sidebar
import codeshield.ui.components.SyncSection
import codeshield.ui.design.Motion
import codeshield.ui.design.ShieldIconButton
import codeshield.ui.design.VDivider
import codeshield.ui.design.WorkspaceTabBar
import codeshield.ui.filterFindings
import codeshield.ui.groupFindings
import codeshield.ui.theme.Shield
import codeshield.ui.theme.ShieldTheme

import codeshield.backend.BackendRunner
import codeshield.backend.NoopBackendRunner
import codeshield.pentest.PentestEngine
import codeshield.pentest.NoopPentestEngine
import codeshield.pentest.ProjectAnalyzer
import codeshield.pentest.NoopProjectAnalyzer
import codeshield.ui.components.PentestSection

/**
 * CodeShield Desktop — shell : sidebar de navigation + panneau des groupes
 * (Findings) + contenu de la section active.
 * Les accès plateforme (processus, fichiers, réseau) sont injectés depuis desktopApp.
 *
 * @param titleBarInset hauteur réservée en haut quand la barre de titre est
 *   transparente (macOS) — la sidebar remonte sous les feux tricolores.
 */
@Composable
fun App(
    runner: CommandRunner,
    fileReader: FileReader,
    syncFactory: SyncClientFactory,
    locator: BinaryLocator,
    dirChecker: DirChecker,
    installer: ToolInstaller,
    onBrowse: (String) -> String?,
    historyStore: ScanHistoryStore = InMemoryHistoryStore(),
    settingsStore: SettingsStore = InMemorySettingsStore(),
    llm: LlmClient = NoopLlmClient,
    backendRunner: BackendRunner = NoopBackendRunner,
    pentestEngine: PentestEngine = NoopPentestEngine,
    projectAnalyzer: ProjectAnalyzer = NoopProjectAnalyzer,
    profiler: codeshield.ui.ProjectProfiler = { codeshield.scanner.ProjectProfile.UNKNOWN },
    workDirs: codeshield.ui.ScanWorkDirs = codeshield.ui.ScanWorkDirs(),
    reportReader: FileReader = fileReader,
    interactiveRunner: codeshield.ui.InteractiveCommandRunner = { command, workDir, _ -> runner(command, workDir) },
    shell: (String) -> List<String> = { listOf("sh", "-c", it) },
    /**
     * Habillage natif (barre de menus, Dock…) fourni par desktopApp. Quand il est présent,
     * les raccourcis ⌘ sont portés par les menus et le gestionnaire clavier interne les ignore.
     */
    nativeChrome: (@Composable (AppCommands) -> Unit)? = null,
    titleBarInset: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember {
        ScannerViewModel(
            scope, runner, fileReader, syncFactory, locator, dirChecker, installer,
            historyStore = historyStore, settingsStore = settingsStore, llm = llm,
            backendRunner = backendRunner, pentestEngine = pentestEngine,
            projectAnalyzer = projectAnalyzer,
            profiler = profiler, workDirs = workDirs, reportReader = reportReader,
            interactiveRunner = interactiveRunner,
            shell = shell,
        )
    }
    val state by viewModel.state.collectAsState()

    // Statuts des moteurs gérés dès l'ouverture (badges ready/missing/download) + santé backend.
    LaunchedEffect(Unit) {
        viewModel.observeBackend()
        viewModel.refreshTools()
        viewModel.refreshAgents()
        viewModel.refreshHistory()
        viewModel.refreshSettings()
        viewModel.checkBackendHealth()
    }

    var section by remember { mutableStateOf(AppSection.FINDINGS) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(GroupTab.FOLDERS) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var panelCollapsed by remember { mutableStateOf(false) }
    var groupsDrag by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }
    val groupsWidth = groupsDrag ?: state.llmSettings.groupsPaneWidth.takeIf { it > 0 }?.dp ?: 260.dp
    val rootFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }

    val update = { transform: (codeshield.ui.ScannerUiState) -> codeshield.ui.ScannerUiState ->
        viewModel.update(transform)
    }

    val groups: List<FindingGroup> = remember(state.findings, tab) {
        groupFindings(state.findings, tab)
    }
    // La sélection ne survit que si le groupe existe toujours.
    val effectiveGroup = selectedGroup?.takeIf { key -> groups.any { it.key == key } }
    val searched = remember(state.findings, query) { filterFindings(state.findings, query) }
    val visible = remember(searched, effectiveGroup) {
        val group = groups.find { it.key == effectiveGroup }
        if (group == null) {
            searched
        } else {
            val keys = searched.map { it.identityKey() }.toSet()
            group.findings.filter { it.identityKey() in keys }
        }
    }
    val breadcrumb = groups.find { it.key == effectiveGroup }?.title ?: "All findings"
    val dirValid = remember(state.projectDir) {
        state.projectDir.isNotBlank() && runCatching { dirChecker(state.projectDir) }.getOrDefault(false)
    }
    val enginesReady = state.toolStatuses.values.count { it.state == ToolState.READY }
    val browse = { inNewTab: Boolean ->
        onBrowse(state.projectDir)?.let { dir -> viewModel.openProject(dir, inNewTab = inNewTab) }
    }
    val runScan = { viewModel.runScan(); section = AppSection.PIPELINE }

    // Projets consultés : fusionne les projets récents et l'historique des scans
    val consultedProjects = remember(state.llmSettings.recentProjects, state.history, state.projectDir) {
        val allPaths = (
            (if (state.projectDir.isNotBlank()) listOf(state.projectDir) else emptyList()) +
            state.llmSettings.recentProjects +
            state.history.map { it.projectDir }
        ).distinct()

        allPaths.map { path ->
            val lastScan = state.history.firstOrNull { it.projectDir == path }
            codeshield.ui.ConsultedProject(
                path = path,
                findingsCount = if (path == state.projectDir) state.findings.size else lastScan?.findingsCount,
                lastScanEpochMs = lastScan?.finishedAtEpochMs,
            )
        }
    }

    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    val language = state.llmSettings.language
    val strings = remember(language) { codeshield.ui.i18n.stringsFor(language) }

    androidx.compose.runtime.CompositionLocalProvider(
        codeshield.ui.i18n.LocalStrings provides strings,
    ) {
        if (nativeChrome != null) {
            val focusSearch = {
                section = AppSection.FINDINGS
                runCatching { searchFocus.requestFocus() }
                Unit
            }
            nativeChrome(
                AppCommands(
                    state = state,
                    section = section,
                    goTo = { section = it },
                    openProject = { browse(false) },
                    newTab = { browse(true) },
                    closeTab = { viewModel.closeTab(state.activeTabId) },
                    openRecent = { viewModel.openProject(it) },
                    recentProjects = consultedProjects.map { it.path },
                    canScan = dirValid && !state.isScanning,
                    runScan = { if (dirValid && !state.isScanning) runScan() },
                    cancelScan = { viewModel.cancelScan() },
                    sync = { viewModel.sync(); section = AppSection.HEALTH },
                    urgentCount = remember(state.findings) { codeshield.agent.ClaudeCodeFix.batchTargets(state.findings).size },
                    fixUrgent = { section = AppSection.FINDINGS; viewModel.fixCriticalAndHighWithClaudeCode() },
                    cancelFix = { viewModel.cancelClaudeFix() },
                    setAgent = { k -> viewModel.updateSettings { it.copy(codingAgent = k) } },
                    setAutoApprove = { on -> viewModel.updateSettings { it.copy(claudeAutoApprove = on) } },
                    respondPermission = { id, allow, all -> viewModel.respondClaudePermission(id, allow, all) },
                    focusSearch = focusSearch,
                    setTheme = { t -> viewModel.updateSettings { it.copy(theme = t) } },
                    setLanguage = { l -> viewModel.updateSettings { it.copy(language = l) } },
                ),
            )
        }
        ShieldTheme(theme = state.llmSettings.theme) {
            Row(
                modifier = Modifier.fillMaxSize().background(Shield.Bg)
                    .focusRequester(rootFocus)
                    .focusable()
                    // Raccourcis : ⌘1…6 sections, ⌘K recherche, ⌘R scan, ⌘T new tab, ⌘W close tab
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || !(e.isMetaPressed || e.isCtrlPressed)) return@onPreviewKeyEvent false
                        // Avec les menus natifs, ce sont eux qui portent ces raccourcis (sinon double action).
                        if (nativeChrome != null && e.key != Key.Tab) return@onPreviewKeyEvent false
                        val sections = AppSection.entries
                        val idx = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six).indexOf(e.key)
                        when {
                            idx in sections.indices -> { section = sections[idx]; true }
                            e.key == Key.T -> { browse(true); true }
                            e.key == Key.W -> { viewModel.closeTab(state.activeTabId); true }
                            e.key == Key.Tab -> {
                                val curIdx = state.tabs.indexOfFirst { it.id == state.activeTabId }
                                if (curIdx >= 0 && state.tabs.isNotEmpty()) {
                                    val nextIdx = (curIdx + 1) % state.tabs.size
                                    viewModel.selectTab(state.tabs[nextIdx].id)
                                }
                                true
                            }
                            e.key == Key.K -> {
                                section = AppSection.FINDINGS
                                runCatching { searchFocus.requestFocus() }
                                true
                            }
                            e.key == Key.R && dirValid && !state.isScanning -> { runScan(); true }
                            else -> false
                        }
                    },
            ) {
                Sidebar(
                    active = section,
                    onSelect = { section = it },
                    projectDir = state.projectDir,
                    consultedProjects = consultedProjects,
                    onSelectProject = { path -> viewModel.openProject(path) },
                    onPickProject = { browse(false) },
                    onClearRecentProjects = { viewModel.clearRecentProjects() },
                    findingsCount = state.findings.size,
                    isScanning = state.isScanning,
                    backendOnline = state.backendState.running,
                    enginesReady = enginesReady,
                    enginesTotal = maxOf(3, state.toolStatuses.size),
                    topInset = titleBarInset,
                    onToggleLanguage = {
                        viewModel.updateSettings {
                            it.copy(language = if (it.language == codeshield.ui.i18n.AppLanguage.FR) codeshield.ui.i18n.AppLanguage.EN else codeshield.ui.i18n.AppLanguage.FR)
                        }
                    },
                )
                VDivider()

                Column(Modifier.weight(1f).fillMaxHeight()) {
                // Bande réservée sous la barre de titre transparente (zone de drag macOS).
                if (titleBarInset > 0.dp) Box(Modifier.fillMaxWidth().height(titleBarInset))
                WorkspaceTabBar(
                    tabs = state.tabs,
                    activeTabId = state.activeTabId,
                    onSelectTab = { viewModel.selectTab(it) },
                    onCloseTab = { viewModel.closeTab(it) },
                    onNewTab = { browse(true) },
                )
                Row(Modifier.fillMaxSize()) {
                    if (section == AppSection.FINDINGS) {
                        if (!panelCollapsed) {
                            SidePanel(
                                tab = tab,
                                onTab = { tab = it; selectedGroup = null },
                                groups = if (query.isBlank()) groups else groupFindings(searched, tab),
                                expanded = expanded,
                                onToggleGroup = { key ->
                                    expanded = if (key in expanded) expanded - key else expanded + key
                                },
                                selectedGroup = effectiveGroup,
                                onSelectGroup = { selectedGroup = it },
                                selectedFinding = state.selected,
                                onSelectFinding = { viewModel.select(it) },
                                onCollapse = { panelCollapsed = true },
                                totalCount = searched.size,
                                width = groupsWidth,
                            )
                            // Séparateur à glisser ; double-clic : largeur par défaut.
                            codeshield.ui.design.ResizeHandle(
                                onDrag = { dx -> groupsDrag = (groupsWidth + dx).coerceIn(200.dp, 480.dp) },
                                onDragEnd = { groupsDrag?.let { w -> viewModel.updateSettings { it.copy(groupsPaneWidth = w.value.toInt()) } } },
                                onReset = { groupsDrag = null; viewModel.updateSettings { it.copy(groupsPaneWidth = 0) } },
                            )
                        } else {
                            // Liseré de réouverture du panneau des groupes.
                            Box(
                                Modifier.width(44.dp).fillMaxHeight().background(Shield.Surface).padding(top = 12.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                ShieldIconButton(Icons.Outlined.KeyboardDoubleArrowRight, "Expand panel", { panelCollapsed = false })
                            }
                            VDivider()
                        }
                    }

                    // Changement d'écran : l'ancien s'efface vite, le nouveau monde en fondu.
                    AnimatedContent(
                        targetState = section,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        transitionSpec = {
                            (fadeIn(Motion.base(delayMs = 60)) + slideInVertically(Motion.base(delayMs = 60)) { 14 }) togetherWith
                                fadeOut(Motion.fast())
                        },
                        label = "section",
                    ) { shown ->
                        when (shown) {
                            AppSection.OVERVIEW -> OverviewSection(
                                state = state,
                                dirValid = dirValid,
                                onDirChange = { viewModel.openProject(it) },
                                onBrowse = { browse(false) },
                                onToggleEngine = { tool, on -> viewModel.toggleEngine(tool, on) },
                                onToggleAutoDetect = { viewModel.setAutoDetectEngines(it) },
                                onCancel = { viewModel.cancelScan() },
                                onInstall = { viewModel.installTool(it) },
                                // Bascule vers Pipeline pour voir la progression en direct.
                                onRun = { runScan() },
                                onStartBackend = { viewModel.startBackend() },
                                onRunPentest = { viewModel.runPentest(); section = AppSection.PENTEST },
                                onOpenFindings = { section = AppSection.FINDINGS },
                            )
                            AppSection.FINDINGS -> FindingsSection(
                                breadcrumb = breadcrumb,
                                onClearFilter = { selectedGroup = null },
                                groups = if (query.isBlank()) groups else groupFindings(searched, tab),
                                selectedGroup = effectiveGroup,
                                onSelectGroup = { selectedGroup = it },
                                visible = visible,
                                selected = state.selected,
                                fileLines = state.fileLines,
                                onSelect = { viewModel.select(it) },
                                onCloseDiff = { viewModel.select(null) },
                                fileError = state.fileError,
                                allFindings = state.findings,
                                onOpenInIde = { viewModel.openInIde(it) },
                                aiExplanation = state.aiExplanation,
                                aiLoading = state.aiLoading,
                                aiError = state.aiError,
                                onExplainAi = { viewModel.explainSelectedWithAi() },
                                aiTriage = state.aiTriage,
                                aiTriageLoading = state.aiTriageLoading,
                                aiTriageError = state.aiTriageError,
                                onTriageAi = { viewModel.triageWithAi() },
                                onDismissTriage = { viewModel.dismissTriage() },
                                llmEnabled = state.llmSettings.enabled && state.llmSettings.provider != codeshield.llm.LlmProvider.NONE,
                                query = query,
                                onQuery = { query = it },
                                searchFocus = searchFocus,
                                onRunScan = { if (dirValid) runScan() else section = AppSection.OVERVIEW },
                                isScanning = state.isScanning,
                                canScan = true,
                                claudeFix = state.claudeFix,
                                onFixWithClaude = { viewModel.fixWithClaudeCode(it) },
                                onCancelClaudeFix = { viewModel.cancelClaudeFix() },
                                onFixCriticalAndHigh = { viewModel.fixCriticalAndHighWithClaudeCode() },
                                onDismissClaudeFix = { viewModel.dismissClaudeFix() },
                                agent = codeshield.ui.components.AgentControls(
                                    kind = state.llmSettings.codingAgent,
                                    paths = state.agentPaths,
                                    onSelect = { k -> viewModel.updateSettings { it.copy(codingAgent = k) } },
                                    autoApprove = state.llmSettings.claudeAutoApprove,
                                    onAutoApprove = { on -> viewModel.updateSettings { it.copy(claudeAutoApprove = on) } },
                                    onRespondPermission = { id, allow, all -> viewModel.respondClaudePermission(id, allow, all) },
                                    onRunBlocked = { viewModel.runBlockedCommand(it) },
                                    onDismissBlocked = { viewModel.dismissBlockedCommand(it) },
                                ),
                                detailWidthDp = state.llmSettings.detailPaneWidth,
                                onDetailWidthChange = { w -> viewModel.updateSettings { it.copy(detailPaneWidth = w) } },
                            )
                            AppSection.PIPELINE -> PipelineSection(
                                state = state,
                                onRun = { viewModel.runScan() },
                                onRestore = { viewModel.restoreScan(it) },
                                onClearHistory = { viewModel.clearHistoryFor(state.projectDir) },
                            )
                            AppSection.SETTINGS -> SettingsSection(
                                settings = state.llmSettings,
                                onChange = { viewModel.updateSettings { _ -> it } },
                                onTest = { viewModel.testLlm() },
                                testing = state.llmTesting,
                                testMessage = state.llmTestMessage,
                                testOk = state.llmTestOk,
                                onFetchModels = { viewModel.fetchModels() },
                                modelsLoading = state.modelsLoading,
                                fetchedModels = state.fetchedModels,
                                modelsMessage = state.modelsMessage,
                            )
                            AppSection.PENTEST -> PentestSection(
                                state = state,
                                onStartBackend = { viewModel.startBackend() },
                                onCheckHealth = { viewModel.checkBackendHealth() },
                                onRunPentest = { viewModel.runPentest() },
                                onAnalyze = { viewModel.analyzeProject() },
                                onBrowse = { browse(false) },
                                onDiagnoseWithLlm = { viewModel.diagnoseBackendWithLlm() },
                                onExecuteQuickFix = { viewModel.executeQuickFix(it) },
                                onServerUrlChange = { viewModel.setServerUrl(it) },
                                onCustomStartCommandChange = { viewModel.setCustomStartCommand(it) },
                                onToggleOfflineMode = { viewModel.setPentestOfflineMode(it) },
                            )
                            AppSection.HEALTH -> SyncSection(
                                state = state,
                                onServerChange = { update { s -> s.copy(serverUrl = it) } },
                                onProjectIdChange = { update { s -> s.copy(projectId = it) } },
                                onTokenChange = { update { s -> s.copy(apiToken = it) } },
                                onSync = { viewModel.sync() },
                            )
                        }
                    }
                }
            }
        }
    }
}
}

