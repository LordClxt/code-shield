package codeshield.ui

import codeshield.agent.ClaudeCodeFix
import codeshield.agent.ClaudeEvent
import codeshield.agent.parseClaudeStreamLine
import codeshield.model.Finding
import codeshield.model.ScanPayload
import codeshield.model.ToolName
import codeshield.cache.InMemoryHistoryStore
import codeshield.cache.PersistedScan
import codeshield.cache.PersistedScanMeta
import codeshield.cache.ScanHistoryStore
import codeshield.cache.currentTimeMs
import codeshield.cache.formatTs
import codeshield.llm.InMemorySettingsStore
import codeshield.llm.LlmClient
import codeshield.llm.LlmSettings
import codeshield.llm.NoopLlmClient
import codeshield.llm.SettingsStore
import codeshield.llm.buildExplainPrompt
import codeshield.llm.buildTriagePrompt
import codeshield.logic.explainFinding
import codeshield.logic.Fingerprint
import codeshield.scanner.EngineOptions
import codeshield.scanner.OutputMode
import codeshield.scanner.Prerequisites
import codeshield.scanner.ProjectProfile
import codeshield.scanner.ScannerEngine
import codeshield.scanner.ScannerEngines
import codeshield.tools.ToolInstaller
import codeshield.tools.ToolState
import codeshield.tools.ToolStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestre les scans CLI et expose l'état pour Compose.
 * Les dépendances sont injectées -> logique testable sans ProcessBuilder ni réseau.
 * Aucune exception ne s'échappe : tout échec finit en ligne `[error]` du terminal
 * (le crash natif `Cannot run program` venait d'une IOException non catchée).
 */
class ScannerViewModel(
    private val scope: CoroutineScope,
    private val runner: CommandRunner,
    private val fileReader: FileReader,
    private val syncFactory: SyncClientFactory,
    private val locator: BinaryLocator,
    private val dirChecker: DirChecker,
    private val installer: ToolInstaller,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val historyStore: ScanHistoryStore = InMemoryHistoryStore(),
    private val settingsStore: SettingsStore = InMemorySettingsStore(),
    private val llm: LlmClient = NoopLlmClient,
    private val backendRunner: codeshield.backend.BackendRunner = codeshield.backend.NoopBackendRunner,
    private val pentestEngine: codeshield.pentest.PentestEngine = codeshield.pentest.NoopPentestEngine,
    private val projectAnalyzer: codeshield.pentest.ProjectAnalyzer = codeshield.pentest.NoopProjectAnalyzer,
    private val profiler: ProjectProfiler = { ProjectProfile.UNKNOWN },
    private val workDirs: ScanWorkDirs = ScanWorkDirs(),
    /** Lecture des rapports JSON (sans la limite de taille du visualiseur). */
    private val reportReader: FileReader = fileReader,
    private val maxParallelEngines: Int = 2,
    /** Exécution avec entrée standard (Claude Code) ; par défaut, l'entrée est ignorée. */
    private val inputRunner: InputCommandRunner = { command, workDir, _ -> runner(command, workDir) },
) {
    private var scanJob: Job? = null
    private val _state = MutableStateFlow(ScannerUiState(backendState = backendRunner.state.value))
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private var backendObserved = false

    /**
     * Reflète l'état du runner backend dans l'UI (démarrage, en ligne, erreur).
     * Appelé une fois au démarrage de l'app. Volontairement hors `init` :
     * un collect infini dans `init` bloquerait les tests runTest
     * (UncompletedCoroutinesError).
     */
    fun observeBackend() {
        if (backendObserved) return
        backendObserved = true
        scope.launch {
            backendRunner.state.collect { bs ->
                _state.update { it.copy(backendState = bs) }
            }
        }
    }

    fun update(transform: (ScannerUiState) -> ScannerUiState) = _state.update(transform)

    /** Binaire effectif : installation gérée prioritaire, PATH système en fallback. */
    private fun effectiveBinary(tool: ToolName): String? =
        runCatching { installer.installedPath(tool) }.getOrNull()
            ?: runCatching { locator(ScannerEngines.of(tool).binary) }.getOrNull()

    fun toggleEngine(tool: ToolName, enabled: Boolean) =
        _state.update { it.copy(enabledEngines = if (enabled) it.enabledEngines + tool else it.enabledEngines - tool) }

    fun setAutoDetectEngines(enabled: Boolean) = _state.update { it.copy(autoDetectEngines = enabled) }

    /** Chemin absolu d'un fichier de finding (les chemins sont relatifs à la racine scannée). */
    internal fun resolveInProject(path: String, projectDir: String = _state.value.projectDir): String {
        val p = path.replace('\\', '/')
        if (p.startsWith("/") || Regex("^[A-Za-z]:/").containsMatchIn(p) || projectDir.isBlank()) return path
        return projectDir.trimEnd('/', '\\') + "/" + p
    }

    /** Rafraîchit les statuts des moteurs (appelée au démarrage + après install). */
    fun refreshTools() {
        scope.launch(ioDispatcher) {
            val statuses = ToolName.entries.associateWith { tool ->
                val managed = runCatching { installer.status(tool) }.getOrNull()
                if (managed?.state == ToolState.READY && managed.path != null) {
                    managed
                } else {
                    val system = effectiveBinary(tool)
                    if (system != null) ToolStatus(tool, ToolState.READY, path = system)
                    else ToolStatus(tool, ToolState.MISSING)
                }
            }
            _state.update { it.copy(toolStatuses = statuses) }
        }
    }

    /** Télécharge + installe un moteur manquant (géré, avec progression). */
    fun installTool(tool: ToolName) {
        if (_state.value.toolStatuses[tool]?.state == ToolState.INSTALLING) return
        scope.launch(ioDispatcher) {
            _state.update { it.copy(toolStatuses = it.toolStatuses + (tool to ToolStatus(tool, ToolState.INSTALLING, progress = 0f))) }
            append("$ installing ${tool.name.lowercase()} (managed tools)…")
            val path = runCatching {
                installer.install(tool) { p ->
                    _state.update { s ->
                        val cur = s.toolStatuses[tool]
                        if (cur?.state == ToolState.INSTALLING) {
                            s.copy(toolStatuses = s.toolStatuses + (tool to cur.copy(progress = p)))
                        } else s
                    }
                }.getOrThrow()
            }
            path.fold(
                onSuccess = {
                    append("Installed ${tool.name.lowercase()} -> $it")
                    refreshTools()
                },
                onFailure = { e ->
                    append("[error] Installation de ${tool.name.lowercase()} échouée : ${e.message}")
                    _state.update { s ->
                        s.copy(toolStatuses = s.toolStatuses + (tool to ToolStatus(tool, ToolState.MISSING, error = e.message)))
                    }
                },
            )
        }
    }

    fun select(finding: Finding?) {
        _state.update {
            it.copy(selected = finding, fileLines = emptyList(), fileError = null, aiExplanation = null, aiError = null)
        }
        if (finding == null) return
        val abs = resolveInProject(finding.filePath)
        scope.launch(ioDispatcher) {
            val result = runCatching { fileReader(abs) }
            _state.update {
                if (it.selected != finding) it
                else result.fold(
                    onSuccess = { lines ->
                        it.copy(fileLines = lines, fileError = if (lines.isEmpty()) "Fichier vide ou illisible : $abs" else null)
                    },
                    onFailure = { e -> it.copy(fileLines = emptyList(), fileError = e.message ?: "Lecture impossible : $abs") },
                )
            }
        }
    }

    /**
     * Ouvre la faille dans l'éditeur local (VS Code, Cursor, puis IntelliJ),
     * positionné sur la ligne. Aucun réseau : simple lancement de processus.
     */
    fun openInIde(finding: Finding) {
        val abs = resolveInProject(finding.filePath)
        scope.launch(ioDispatcher) {
            val editors = listOf(
                "code" to listOf("-g", "$abs:${finding.lineNumber}:${finding.column ?: 1}"),
                "cursor" to listOf("-g", "$abs:${finding.lineNumber}:${finding.column ?: 1}"),
                "idea" to listOf("--line", finding.lineNumber.toString(), abs),
            )
            val (bin, args) = editors.firstNotNullOfOrNull { (name, args) ->
                runCatching { locator(name) }.getOrNull()?.let { it to args }
            } ?: run {
                append("[error] Aucun éditeur trouvé dans le PATH (code, cursor, idea).")
                return@launch
            }
            runCatching { runner(listOf(bin) + args, _state.value.projectDir.ifBlank { null }).collect { } }
                .onFailure { append("[error] Ouverture dans l'éditeur impossible : ${it.message}") }
        }
    }

    /** Sélectionne la finding suivante/précédente dans la liste (raccourcis j/k). */
    fun selectRelative(delta: Int, ordered: List<Finding> = _state.value.findings) {
        if (ordered.isEmpty()) return
        val idx = ordered.indexOf(_state.value.selected)
        val next = if (idx < 0) 0 else (idx + delta).coerceIn(0, ordered.lastIndex)
        select(ordered[next])
    }

    private fun setScanning(scanning: Boolean, extra: (ScannerUiState) -> ScannerUiState = { it }) {
        _state.update { curr ->
            val updatedTabs = curr.tabs.map { t -> if (t.id == curr.activeTabId) t.copy(isScanning = scanning) else t }
            extra(curr.copy(tabs = updatedTabs, isScanning = scanning))
        }
    }

    private fun setEngine(run: EngineRun) = _state.update { it.copy(engineRuns = it.engineRuns + (run.tool to run)) }

    /** Interrompt le scan en cours : les processus des moteurs sont tués. */
    fun cancelScan() {
        val job = scanJob ?: return
        if (!job.isActive) return
        job.cancel()
        append("[info] Scan annulé par l'utilisateur.")
        _state.update { s ->
            s.copy(engineRuns = s.engineRuns.mapValues { (_, r) ->
                if (r.state == EngineState.RUNNING || r.state == EngineState.QUEUED) r.copy(state = EngineState.CANCELLED) else r
            })
        }
        setScanning(false)
    }

    fun runScan() {
        val snapshot = _state.value
        if (snapshot.isScanning || snapshot.projectDir.isBlank()) return
        setScanning(true) { curr ->
            val updatedTabs = curr.tabs.map { t ->
                if (t.id == curr.activeTabId) t.copy(terminal = emptyList(), findings = emptyList()) else t
            }
            curr.copy(tabs = updatedTabs, terminal = emptyList(), findings = emptyList(), syncMessage = null, engineRuns = emptyMap())
        }
        scanJob = scope.launch(ioDispatcher) {
            try {
                scanProject(snapshot)
            } finally {
                if (_state.value.isScanning) setScanning(false)
            }
        }
    }

    private suspend fun scanProject(snapshot: ScannerUiState) {
        val projectDir = snapshot.projectDir
        // Pré-vol 1 : le dossier existe-t-il ?
        if (!runCatching { dirChecker(projectDir) }.getOrDefault(false)) {
            append("[error] Dossier introuvable : $projectDir")
            append("[error] Corrigez le chemin (Browse…) puis relancez.")
            return
        }

        // Pré-vol 2 : quels moteurs ont quelque chose à analyser ?
        val config = snapshot.toScannerConfig()
        val profile = runCatching { profiler(projectDir) }.getOrDefault(ProjectProfile.UNKNOWN)
        val engines = config.enginesFor(profile)
        for (tool in config.enabled - engines.map { it.tool }.toSet()) {
            val e = ScannerEngines.of(tool)
            append("[info] ${e.label} ignoré : rien à analyser dans ce projet (auto-détection).")
            setEngine(EngineRun(tool, EngineState.SKIPPED, message = "Rien à analyser"))
        }
        if (engines.isEmpty()) {
            append("[error] Aucun moteur activé pour ce projet — cochez au moins un moteur.")
            return
        }

        // Pré-vol 3 : quels binaires sont réellement exécutables ?
        val runnable = engines.mapNotNull { engine ->
            val resolved = runCatching { effectiveBinary(engine.tool) }.getOrNull()
            if (resolved == null) {
                val auto = ToolStatus(engine.tool, ToolState.MISSING).autoInstallable
                val hint = if (auto) "cliquez Download dans Overview" else Prerequisites.hintFor(engine.binary)
                append("[error] `${engine.binary}` introuvable — $hint. Moteur ignoré.")
                setEngine(EngineRun(engine.tool, EngineState.MISSING, message = hint))
                null
            } else {
                setEngine(EngineRun(engine.tool, EngineState.QUEUED))
                engine to resolved
            }
        }
        if (runnable.isEmpty()) {
            append("[error] Aucun scanner exécutable — installez-en un puis relancez.")
            return
        }

        val workDir = runCatching { workDirs.create() }.getOrElse {
            append("[error] Impossible de créer le dossier temporaire : ${it.message}")
            return
        }
        val raw = try {
            val permits = Semaphore(maxParallelEngines.coerceAtLeast(1))
            coroutineScope {
                runnable.map { (engine, binary) ->
                    async { permits.withPermit { runEngine(engine, binary, workDir, projectDir) } }
                }.awaitAll().flatten()
            }
        } finally {
            runCatching { workDirs.delete(workDir) }
        }
        // Plusieurs moteurs peuvent remonter la même faille (ex. Trivy + OSV sur une CVE).
        val findings = raw
            .distinctBy { listOf(it.tool, it.ruleId, it.filePath, it.lineNumber) }
            .sortedWith(compareBy({ it.severity.ordinal }, { it.filePath }, { it.lineNumber }))
        val finishedAt = currentTimeMs()
        setScanning(false) { curr ->
            val updatedTabs = curr.tabs.map { t ->
                if (t.id == curr.activeTabId) t.copy(findings = findings, lastScanAt = finishedAt, restoreMessage = null) else t
            }
            curr.copy(tabs = updatedTabs, findings = findings, lastScanAt = finishedAt, restoreMessage = null)
        }
        append("Terminé : ${findings.size} findings (métadonnées uniquement).")
        // Persistance locale : le scan survit au redémarrage (historique).
        runCatching {
            val terminalSnapshot = _state.value.terminal
            historyStore.save(PersistedScan(projectDir, findings, terminalSnapshot, finishedAt))
            val hist = historyStore.list()
            _state.update { it.copy(history = hist) }
            // Ajout automatique au catalogue des projets récents consultés
            val cur = _state.value.llmSettings.recentProjects
            val updated = (listOf(projectDir) + cur.filter { it != projectDir }).take(10)
            updateSettings { it.copy(recentProjects = updated) }
        }
    }

    /**
     * Exécute un moteur (timeout + annulation) et parse sa sortie.
     * Toute erreur devient une ligne `[error]` du terminal : jamais d'exception.
     */
    private suspend fun runEngine(engine: ScannerEngine, binary: String, workDir: String, projectDir: String): List<Finding> {
        val tag = "[${engine.tool.name.lowercase()}]"
        val started = currentTimeMs()
        setEngine(EngineRun(engine.tool, EngineState.RUNNING))
        val command = engine.command(binary, workDir, EngineOptions())
        append("$tag $ ${command.joinToString(" ")}")
        val stdout = StringBuilder()
        var exitCode: Int? = null
        val outcome = runCatching {
            withTimeoutOrNull(engine.timeoutMs) {
                runner(command, projectDir).collect { line ->
                    when (line) {
                        is TerminalLine.Out -> {
                            if (engine.outputMode == OutputMode.STDOUT) stdout.appendLine(line.text)
                            // Le JSON brut (souvent une seule ligne géante) n'encombre pas le terminal.
                            if (engine.outputMode != OutputMode.STDOUT || !line.text.trimStart().let { it.startsWith("{") || it.startsWith("[") || it.startsWith("\"") || it.startsWith("}") || it.startsWith("]") }) {
                                append("$tag ${line.text}")
                            }
                        }
                        is TerminalLine.Err -> append("$tag [stderr] ${line.text}")
                        is TerminalLine.Exit -> exitCode = line.code
                    }
                }
                true
            }
        }
        val elapsed = currentTimeMs() - started
        val failure = outcome.exceptionOrNull()
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        when {
            failure != null -> {
                append("[error] $tag Échec d'exécution : ${failure.message}")
                setEngine(EngineRun(engine.tool, EngineState.FAILED, durationMs = elapsed, message = failure.message))
                return emptyList()
            }
            outcome.getOrNull() == null -> {
                append("[error] $tag Délai dépassé (${engine.timeoutMs / 60_000} min) — moteur interrompu.")
                setEngine(EngineRun(engine.tool, EngineState.TIMEOUT, durationMs = elapsed))
                return emptyList()
            }
        }
        val code = exitCode
        if (code != null && !engine.isExpectedExit(code)) {
            append("[error] $tag a échoué (code $code).")
            setEngine(EngineRun(engine.tool, EngineState.FAILED, durationMs = elapsed, message = "exit $code"))
            return emptyList()
        }
        val rawJson = when (engine.outputMode) {
            OutputMode.STDOUT -> stdout.toString()
            OutputMode.REPORT_FILE -> runCatching { reportReader(engine.reportPath(workDir)).joinToString("\n") }.getOrDefault("")
        }
        val parsed = engine.parse(rawJson, projectDir)
        append("$tag ${parsed.size} findings (${elapsed / 1000}s).")
        setEngine(EngineRun(engine.tool, EngineState.DONE, findings = parsed.size, durationMs = elapsed))
        return parsed
    }

    private var tabCounter = 1

    /**
     * Crée un nouvel onglet de projet/workspace et bascule dessus si activate = true.
     */
    fun createTab(projectDir: String = "", activate: Boolean = true): String {
        tabCounter++
        val newId = "tab-$tabCounter"
        val clean = projectDir.trim()
        val newTab = WorkspaceTabState(id = newId, projectDir = clean)
        _state.update { current ->
            current.copy(tabs = current.tabs + newTab)
        }
        if (clean.isNotBlank()) {
            val cur = _state.value.llmSettings.recentProjects
            val updated = (listOf(clean) + cur.filter { it != clean }).take(10)
            updateSettings { it.copy(recentProjects = updated) }

            scope.launch(ioDispatcher) {
                val saved = runCatching { historyStore.load(clean) }.getOrNull()
                if (saved != null) {
                    _state.update { curr ->
                        val updatedTabs = curr.tabs.map { t ->
                            if (t.id == newId) {
                                t.copy(
                                    terminal = saved.terminal,
                                    findings = saved.findings,
                                    lastScanAt = saved.finishedAtEpochMs,
                                    restoreMessage = "Scan du ${formatTs(saved.finishedAtEpochMs)} restauré (${saved.findings.size} findings).",
                                )
                            } else t
                        }
                        if (curr.activeTabId == newId) {
                            curr.copy(
                                tabs = updatedTabs,
                                terminal = saved.terminal,
                                findings = saved.findings,
                                lastScanAt = saved.finishedAtEpochMs,
                                restoreMessage = "Scan du ${formatTs(saved.finishedAtEpochMs)} restauré (${saved.findings.size} findings).",
                            )
                        } else {
                            curr.copy(tabs = updatedTabs)
                        }
                    }
                }
            }
            if (!activate) {
                analyzeProject(clean)
            }
        }
        if (activate) {
            selectTab(newId)
        }
        return newId
    }

    /**
     * Active un onglet existant par son ID en restaurant son état complet.
     */
    fun selectTab(tabId: String) {
        _state.update { current ->
            if (current.activeTabId == tabId) return@update current
            // Sauvegarder l'état courant dans l'onglet actif actuel
            val savedTabs = current.tabs.map { tab ->
                if (tab.id == current.activeTabId) {
                    tab.copy(
                        projectDir = current.projectDir,
                        findings = current.findings,
                        terminal = current.terminal,
                        isScanning = current.isScanning,
                        selected = current.selected,
                        fileLines = current.fileLines,
                        lastScanAt = current.lastScanAt,
                        restoreMessage = current.restoreMessage,
                        pentestReport = current.pentestReport,
                        pentestAnalysis = current.pentestAnalysis,
                        isPentesting = current.isPentesting,
                        pentestProgress = current.pentestProgress,
                        aiExplanation = current.aiExplanation,
                        aiLoading = current.aiLoading,
                        aiError = current.aiError,
                        aiTriage = current.aiTriage,
                        aiTriageLoading = current.aiTriageLoading,
                        aiTriageError = current.aiTriageError,
                    )
                } else tab
            }
            val target = savedTabs.find { it.id == tabId } ?: return@update current.copy(tabs = savedTabs)
            current.copy(
                tabs = savedTabs,
                activeTabId = target.id,
                projectDir = target.projectDir,
                findings = target.findings,
                terminal = target.terminal,
                isScanning = target.isScanning,
                selected = target.selected,
                fileLines = target.fileLines,
                lastScanAt = target.lastScanAt,
                restoreMessage = target.restoreMessage,
                pentestReport = target.pentestReport,
                pentestAnalysis = target.pentestAnalysis,
                isPentesting = target.isPentesting,
                pentestProgress = target.pentestProgress,
                aiExplanation = target.aiExplanation,
                aiLoading = target.aiLoading,
                aiError = target.aiError,
                aiTriage = target.aiTriage,
                aiTriageLoading = target.aiTriageLoading,
                aiTriageError = target.aiTriageError,
            )
        }
        val currentTarget = _state.value.tabs.find { it.id == tabId }
        if (currentTarget != null && currentTarget.projectDir.isNotBlank() && currentTarget.pentestAnalysis == null) {
            analyzeProject(currentTarget.projectDir)
        }
    }

    /**
     * Ferme un onglet. Si c'était l'actif, sélectionne le voisin le plus proche.
     * Si c'était le dernier, réinitialise un onglet vierge.
     */
    fun closeTab(tabId: String) {
        _state.update { current ->
            val idx = current.tabs.indexOfFirst { it.id == tabId }
            if (idx < 0) return@update current

            val remaining = current.tabs.filter { it.id != tabId }
            if (remaining.isEmpty()) {
                tabCounter++
                val fresh = WorkspaceTabState(id = "tab-$tabCounter")
                current.copy(
                    tabs = listOf(fresh),
                    activeTabId = fresh.id,
                    projectDir = "",
                    findings = emptyList(),
                    terminal = emptyList(),
                    isScanning = false,
                    selected = null,
                    fileLines = emptyList(),
                    lastScanAt = null,
                    restoreMessage = null,
                    pentestReport = null,
                    pentestAnalysis = null,
                    isPentesting = false,
                    pentestProgress = null,
                    aiExplanation = null,
                    aiLoading = false,
                    aiError = null,
                    aiTriage = null,
                    aiTriageLoading = false,
                    aiTriageError = null,
                )
            } else {
                val newActiveId = if (current.activeTabId == tabId) {
                    val nextIdx = minOf(idx, remaining.lastIndex)
                    remaining[nextIdx].id
                } else {
                    current.activeTabId
                }
                val target = remaining.find { it.id == newActiveId } ?: remaining.first()
                current.copy(
                    tabs = remaining,
                    activeTabId = target.id,
                    projectDir = target.projectDir,
                    findings = target.findings,
                    terminal = target.terminal,
                    isScanning = target.isScanning,
                    selected = target.selected,
                    fileLines = target.fileLines,
                    lastScanAt = target.lastScanAt,
                    restoreMessage = target.restoreMessage,
                    pentestReport = target.pentestReport,
                    pentestAnalysis = target.pentestAnalysis,
                    isPentesting = target.isPentesting,
                    pentestProgress = target.pentestProgress,
                    aiExplanation = target.aiExplanation,
                    aiLoading = target.aiLoading,
                    aiError = target.aiError,
                    aiTriage = target.aiTriage,
                    aiTriageLoading = target.aiTriageLoading,
                    aiTriageError = target.aiTriageError,
                )
            }
        }
    }

    /**
     * Ouvre ou sélectionne un projet :
     * - Si déjà ouvert dans un onglet existant : active cet onglet
     * - Si inNewTab == true : crée un nouvel onglet
     * - Sinon : charge dans l'onglet actif et restaure l'historique
     */
    fun openProject(path: String, inNewTab: Boolean = false) {
        val clean = path.trim()
        if (clean.isBlank()) return

        // 1) Si le projet est déjà ouvert dans un onglet existant, on l'active
        val existing = _state.value.tabs.find { it.projectDir == clean }
        if (existing != null) {
            selectTab(existing.id)
            return
        }

        // 2) Si inNewTab demandé, on crée un nouvel onglet
        if (inNewTab) {
            createTab(clean, activate = true)
            return
        }

        // 3) Sinon on ouvre dans l'onglet actif
        val curActive = _state.value.activeTabId
        val cur = _state.value.llmSettings.recentProjects
        val updated = (listOf(clean) + cur.filter { it != clean }).take(10)
        updateSettings { it.copy(recentProjects = updated) }

        scope.launch(ioDispatcher) {
            val saved = runCatching { historyStore.load(clean) }.getOrNull()
            if (saved != null) {
                _state.update { current ->
                    val updatedTabs = current.tabs.map { t ->
                        if (t.id == curActive) {
                            t.copy(
                                projectDir = clean,
                                terminal = saved.terminal,
                                findings = saved.findings,
                                lastScanAt = saved.finishedAtEpochMs,
                                restoreMessage = "Scan du ${formatTs(saved.finishedAtEpochMs)} restauré (${saved.findings.size} findings).",
                                selected = null,
                                fileLines = emptyList(),
                            )
                        } else t
                    }
                    current.copy(
                        tabs = updatedTabs,
                        projectDir = clean,
                        terminal = saved.terminal,
                        findings = saved.findings,
                        lastScanAt = saved.finishedAtEpochMs,
                        restoreMessage = "Scan du ${formatTs(saved.finishedAtEpochMs)} restauré (${saved.findings.size} findings).",
                        selected = null,
                        fileLines = emptyList(),
                    )
                }
            } else {
                _state.update { current ->
                    val updatedTabs = current.tabs.map { t ->
                        if (t.id == curActive) {
                            t.copy(
                                projectDir = clean,
                                terminal = emptyList(),
                                findings = emptyList(),
                                lastScanAt = null,
                                restoreMessage = null,
                                selected = null,
                                fileLines = emptyList(),
                            )
                        } else t
                    }
                    current.copy(
                        tabs = updatedTabs,
                        projectDir = clean,
                        terminal = emptyList(),
                        findings = emptyList(),
                        lastScanAt = null,
                        restoreMessage = null,
                        selected = null,
                        fileLines = emptyList(),
                    )
                }
            }
            analyzeProject(clean)
        }
    }

    /** Efface la liste des projets récemment consultés. */
    fun clearRecentProjects() {
        updateSettings { it.copy(recentProjects = emptyList()) }
    }

    /** Recharge l'historique (appelée au démarrage). */
    fun refreshHistory() {
        scope.launch(ioDispatcher) {
            val hist = runCatching { historyStore.list() }.getOrDefault(emptyList())
            _state.update { it.copy(history = hist) }
        }
    }

    /** Restaure le dernier scan d'un projet (auto-restore au démarrage). */
    fun restoreLastScan(projectDir: String) {
        if (projectDir.isBlank()) return
        scope.launch(ioDispatcher) {
            val saved = runCatching { historyStore.load(projectDir) }.getOrNull() ?: return@launch
            _state.update {
                it.copy(
                    projectDir = saved.projectDir,
                    terminal = saved.terminal,
                    findings = saved.findings,
                    lastScanAt = saved.finishedAtEpochMs,
                    restoreMessage = "Scan du ${formatTs(saved.finishedAtEpochMs)} restauré (${saved.findings.size} findings).",
                    selected = null,
                    fileLines = emptyList(),
                )
            }
            if (_state.value.pentestAnalysis == null) {
                analyzeProject(saved.projectDir)
            }
        }
    }

    /** Restaure un scan précis depuis l'historique. */
    fun restoreScan(meta: PersistedScanMeta) {
        scope.launch(ioDispatcher) {
            val saved = runCatching { historyStore.loadMeta(meta.projectDir, meta.finishedAtEpochMs) }.getOrNull()
                ?: return@launch
            _state.update {
                it.copy(
                    projectDir = saved.projectDir,
                    terminal = saved.terminal,
                    findings = saved.findings,
                    lastScanAt = saved.finishedAtEpochMs,
                    restoreMessage = "Scan du ${formatTs(saved.finishedAtEpochMs)} restauré (${saved.findings.size} findings).",
                    selected = null,
                    fileLines = emptyList(),
                )
            }
        }
    }

    fun clearHistoryFor(projectDir: String) {
        scope.launch(ioDispatcher) {
            runCatching { historyStore.clear(projectDir) }
            val hist = runCatching { historyStore.list() }.getOrDefault(emptyList())
            _state.update { it.copy(history = hist) }
        }
    }

    // -- Settings LLM + thème ---------------------------------------------

    fun refreshSettings() {
        scope.launch(ioDispatcher) {
            val s = runCatching { settingsStore.load() }.getOrDefault(LlmSettings())
            _state.update { it.copy(llmSettings = s) }
        }
    }

    fun updateSettings(transform: (LlmSettings) -> LlmSettings) {
        val prev = _state.value.llmSettings
        val next = transform(prev)
        _state.update {
            // Changement de provider/clé : les modèles fetchés et le test ne sont plus valides.
            val stale = next.provider != prev.provider || next.apiKey != prev.apiKey || next.baseUrl != prev.baseUrl
            it.copy(
                llmSettings = next,
                fetchedModels = if (stale) emptyList() else it.fetchedModels,
                modelsMessage = if (stale) null else it.modelsMessage,
                llmTestMessage = if (stale) null else it.llmTestMessage,
                llmTestOk = if (stale) null else it.llmTestOk,
            )
        }
        scope.launch(ioDispatcher) { runCatching { settingsStore.save(next) } }
    }

    /**
     * Test de connexion LLM avec feedback inline (spinner + message vert/rouge
     * dans Settings, en plus de la ligne terminal historique).
     */
    fun testLlm(onResult: (String) -> Unit = {}) {
        if (_state.value.llmTesting) return
        val s = _state.value.llmSettings
        scope.launch(ioDispatcher) {
            _state.update { it.copy(llmTesting = true, llmTestMessage = null, llmTestOk = null) }
            val res = runCatching { llm.testConnection(s).getOrThrow() }
            val msg = res.fold(onSuccess = { "LLM OK : $it" }, onFailure = { "LLM KO : ${it.message}" })
            _state.update { it.copy(llmTesting = false, llmTestMessage = msg, llmTestOk = res.isSuccess) }
            append(msg)
            onResult(msg)
        }
    }

    /**
     * Récupère la liste réelle des modèles du provider (bouton "Fetch" de Settings).
     * Succès : remplace la liste statique du dropdown. Échec : message inline.
     */
    fun fetchModels() {
        if (_state.value.modelsLoading) return
        val s = _state.value.llmSettings
        scope.launch(ioDispatcher) {
            _state.update { it.copy(modelsLoading = true, modelsMessage = null) }
            val res = runCatching { llm.listModels(s).getOrThrow() }
            res.fold(
                onSuccess = { models ->
                    _state.update {
                        it.copy(
                            modelsLoading = false,
                            fetchedModels = models,
                            modelsMessage = null,
                            // Auto-sélection si aucun modèle courant dans la liste.
                            llmSettings = if (it.llmSettings.model in models) it.llmSettings
                                else it.llmSettings.copy(model = models.first()),
                        )
                    }
                    append("Modèles ${s.provider.label} récupérés : ${models.size} dispo.")
                },
                onFailure = { e ->
                    _state.update { it.copy(modelsLoading = false, modelsMessage = e.message ?: "fetch échoué") }
                    append("[error] Fetch modèles échoué : ${e.message}")
                },
            )
        }
    }

    /**
     * Texte affichable : si l'API a coupé la réponse (limite max tokens),
     * on le dit explicitement au lieu de laisser une phrase interrompue.
     */
    private fun codeshield.llm.LlmResult.displayText(): String =
        if (truncated) {
            text + "\n\n⚠ Réponse tronquée (limite de tokens atteinte) — augmentez « Max tokens » dans Settings."
        } else {
            text
        }

    /**
     * Explique la finding sélectionnée avec le LLM (metadata-only).
     * Fallback : guide local offline si LLM désactivé / erreur.
     */
    fun explainSelectedWithAi() {
        val finding = _state.value.selected ?: return
        val settings = _state.value.llmSettings
        if (_state.value.aiLoading) return
        scope.launch(ioDispatcher) {
            _state.update { it.copy(aiLoading = true, aiError = null) }
            val local = explainFinding(finding)
            val prompt = buildExplainPrompt(finding, local)
            val res = runCatching { llm.complete(prompt, settings).getOrThrow() }
            res.fold(
                onSuccess = { r -> _state.update { it.copy(aiLoading = false, aiExplanation = r.displayText(), aiError = null) } },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            aiLoading = false, aiError = (e.message ?: "LLM indisponible") + " — guide local affiché.",
                            aiExplanation = null,
                        )
                    }
                },
            )
        }
    }

    private var claudeFixJob: Job? = null

    /**
     * Lance Claude Code en local (`claude -p`) dans le dossier du projet pour corriger
     * la faille. Claude lit et modifie les fichiers lui-même ; l'app ne fait que lui
     * donner les métadonnées de la faille et afficher sa progression.
     */
    fun fixWithClaudeCode(finding: Finding) = runClaudeFix(listOf(finding))

    /** Confie en une seule session les failles CRITICAL et HIGH du projet à Claude Code. */
    fun fixCriticalAndHighWithClaudeCode() {
        val targets = ClaudeCodeFix.batchTargets(_state.value.findings)
        if (targets.isEmpty()) {
            append("[claude] Aucune faille CRITICAL ou HIGH à corriger.")
            return
        }
        runClaudeFix(targets)
    }

    private fun runClaudeFix(targets: List<Finding>) {
        if (_state.value.claudeFix?.running == true) return
        val projectDir = _state.value.projectDir
        if (projectDir.isBlank()) return
        _state.update { it.copy(claudeFix = ClaudeFixState(targets)) }
        claudeFixJob = scope.launch(ioDispatcher) {
            val binary = runCatching { locator("claude") }.getOrNull()
            if (binary == null) {
                finishClaudeFix(targets, false, "Claude Code introuvable. Installez-le (https://claude.com/claude-code) puis lancez `claude` une fois pour vous connecter.")
                return@launch
            }
            val command = ClaudeCodeFix.command(binary)
            val prompt = ClaudeCodeFix.prompt(targets)
            val first = targets.first()
            append("[claude] $ claude -p … " + if (targets.size > 1) "(${targets.size} failles CRITICAL/HIGH)" else "(${first.filePath}:${first.lineNumber})")
            val timeoutMs = ClaudeCodeFix.timeoutFor(targets.size)
            var exitCode: Int? = null
            var result: ClaudeEvent.Result? = null
            val stderr = mutableListOf<String>()
            val outcome = runCatching {
                withTimeoutOrNull(timeoutMs) {
                    inputRunner(command, projectDir, prompt).collect { line ->
                        when (line) {
                            is TerminalLine.Out -> for (event in parseClaudeStreamLine(line.text)) {
                                when (event) {
                                    is ClaudeEvent.Text -> logClaudeFix(targets, event.text)
                                    is ClaudeEvent.ToolUse -> logClaudeFix(targets, "→ ${event.tool}" + (event.target?.let { " $it" } ?: ""))
                                    is ClaudeEvent.Result -> result = event
                                }
                            }
                            is TerminalLine.Err -> stderr += line.text
                            is TerminalLine.Exit -> exitCode = line.code
                        }
                    }
                    true
                }
            }
            val failure = outcome.exceptionOrNull()
            if (failure is kotlinx.coroutines.CancellationException) {
                finishClaudeFix(targets, false, "Correction annulée.")
                throw failure
            }
            val r = result
            when {
                failure != null -> finishClaudeFix(targets, false, "Échec du lancement de Claude Code : ${failure.message}")
                outcome.getOrNull() == null -> finishClaudeFix(targets, false, "Délai dépassé (${timeoutMs / 60_000} min) : Claude Code a été interrompu.")
                r != null && !r.isError && exitCode == 0 -> finishClaudeFix(targets, true, r.summary.ifBlank { "Terminé." })
                else -> finishClaudeFix(
                    targets, false,
                    r?.summary?.takeIf { it.isNotBlank() }
                        ?: stderr.takeLast(5).joinToString("\n").ifBlank { "Claude Code s'est arrêté (code ${exitCode ?: "?"})." },
                )
            }
        }
    }

    /** Interrompt la correction en cours (le processus `claude` est tué). */
    fun cancelClaudeFix() {
        claudeFixJob?.takeIf { it.isActive }?.cancel()
    }

    /** Ferme le bandeau d'une correction terminée. */
    fun dismissClaudeFix() {
        _state.update { s -> if (s.claudeFix?.running == true) s else s.copy(claudeFix = null) }
    }

    private fun logClaudeFix(targets: List<Finding>, line: String) {
        append("[claude] ${line.lineSequence().first()}")
        _state.update { s ->
            val fix = s.claudeFix?.takeIf { it.findings == targets } ?: return@update s
            s.copy(claudeFix = fix.copy(log = (fix.log + line).takeLast(300)))
        }
    }

    private fun finishClaudeFix(targets: List<Finding>, success: Boolean, summary: String) {
        append(if (success) "[claude] Correction terminée : relancez le scan pour confirmer." else "[claude] [error] ${summary.lineSequence().first()}")
        _state.update { s ->
            val fix = s.claudeFix?.takeIf { it.findings == targets } ?: return@update s
            s.copy(claudeFix = fix.copy(running = false, success = success, summary = summary))
        }
    }

    /** Triage IA du projet : priorise les findings (metadata-only, max 30). */
    fun triageWithAi() {
        val snapshot = _state.value
        if (snapshot.aiTriageLoading || snapshot.findings.isEmpty()) return
        scope.launch(ioDispatcher) {
            _state.update { it.copy(aiTriageLoading = true, aiTriageError = null) }
            val prompt = buildTriagePrompt(snapshot.projectDir, snapshot.findings)
            val res = runCatching { llm.complete(prompt, snapshot.llmSettings).getOrThrow() }
            res.fold(
                onSuccess = { r ->
                    _state.update { it.copy(aiTriageLoading = false, aiExplanation = null, aiTriage = r.displayText()) }
                },
                onFailure = { e ->
                    val msg = e.message ?: "LLM indisponible"
                    append("[error] Triage IA échoué : $msg")
                    // L'échec est visible dans l'UI (avant : uniquement au terminal).
                    _state.update { it.copy(aiTriageLoading = false, aiTriageError = msg) }
                },
            )
        }
    }

    /** Ferme le panneau de triage IA (résultat ou erreur). */
    fun dismissTriage() {
        _state.update { it.copy(aiTriage = null, aiTriageError = null) }
    }

    fun sync() {
        val snapshot = _state.value
        val projectId = snapshot.projectId.toIntOrNull()
        if (projectId == null || snapshot.apiToken.isBlank()) {
            _state.update { it.copy(syncMessage = "Renseignez projectId, serverUrl et apiToken.") }
            return
        }
        scope.launch(ioDispatcher) {
            _state.update { it.copy(syncMessage = "Synchronisation…") }
            val result = runCatching {
                // Empreintes calculées localement : seul le hash part sur le réseau.
                val fingerprinted = Fingerprint.apply(snapshot.findings, salt = "project:$projectId") { rel ->
                    runCatching { fileReader(resolveInProject(rel, snapshot.projectDir)) }.getOrNull()
                }
                val client = syncFactory(snapshot.serverUrl, snapshot.apiToken)
                client.submit(ScanPayload(projectId, fingerprinted))
            }
            _state.update {
                it.copy(
                    syncMessage = result.fold(
                        onSuccess = { r ->
                            "Sync OK : +${r.created} ~${r.touched} ✓${r.resolved} résolues" +
                                (if (r.reopened > 0) " ↺${r.reopened} réouvertes" else "") +
                                " · ${r.openTotal} ouvertes · score ${r.score} (${r.grade})"
                        },
                        onFailure = { e -> "Sync échouée : ${e.message}" },
                    ),
                )
            }
        }
    }

    fun checkBackendHealth() {
        if (_state.value.isCheckingBackend) return
        scope.launch(ioDispatcher) {
            _state.update { it.copy(isCheckingBackend = true) }
            try {
                backendRunner.checkHealth(_state.value.serverUrl)
            } finally {
                _state.update { it.copy(isCheckingBackend = false) }
            }
        }
    }

    fun setServerUrl(url: String) {
        val activeId = _state.value.activeTabId
        _state.update { curr ->
            val updatedTabs = curr.tabs.map { if (it.id == activeId) it.copy(serverUrl = url) else it }
            curr.copy(serverUrl = url, tabs = updatedTabs)
        }
    }

    fun setCustomStartCommand(cmd: String) {
        val activeId = _state.value.activeTabId
        _state.update { curr ->
            val updatedTabs = curr.tabs.map { if (it.id == activeId) it.copy(customStartCommand = cmd) else it }
            curr.copy(customStartCommand = cmd, tabs = updatedTabs)
        }
    }

    fun setPentestOfflineMode(enabled: Boolean) {
        val activeId = _state.value.activeTabId
        _state.update { curr ->
            val updatedTabs = curr.tabs.map { if (it.id == activeId) it.copy(pentestOfflineMode = enabled) else it }
            curr.copy(pentestOfflineMode = enabled, tabs = updatedTabs)
        }
    }

    fun startBackend() {
        val snapshot = _state.value
        val dir = snapshot.projectDir.ifBlank { null }
        val port = portFromUrl(snapshot.serverUrl)
        val customCmd = snapshot.customStartCommand.trim().takeIf { it.isNotBlank() }?.let { cmdStr ->
            listOf("sh", "-c", cmdStr)
        } ?: snapshot.pentestAnalysis?.startCommand?.takeIf { it.isNotEmpty() }
        scope.launch(ioDispatcher) {
            backendRunner.startBackend(dir, port, customCmd)
        }
    }

    fun stopBackend() {
        scope.launch(ioDispatcher) {
            backendRunner.stopBackend()
        }
    }

    /** Analyse la panne du backend avec le LLM configuré (Gemini, Claude, OpenAI, Ollama). */
    fun diagnoseBackendWithLlm() {
        val bState = _state.value.backendState
        val logs = bState.rawLogs ?: bState.error ?: return
        val dir = bState.projectDir ?: _state.value.projectDir
        val stackLabel = bState.stackLabel ?: "Backend"
        val exitCode = bState.exitCode

        scope.launch(ioDispatcher) {
            _state.update { it.copy(backendState = it.backendState.copy(isDiagnosing = true)) }
            val settings = runCatching { settingsStore.load() }.getOrNull()
            if (settings != null && settings.enabled) {
                val res = codeshield.backend.BackendDiagnosticEngine.diagnoseWithLlm(
                    llm = llm,
                    settings = settings,
                    workDir = dir,
                    stackLabel = stackLabel,
                    exitCode = exitCode,
                    logs = logs,
                )
                val diag = res.getOrNull() ?: bState.diagnostic ?: codeshield.backend.BackendDiagnosticEngine.diagnoseLocally(dir, stackLabel, exitCode, logs)
                _state.update {
                    it.copy(backendState = it.backendState.copy(diagnostic = diag, isDiagnosing = false))
                }
            } else {
                val diag = bState.diagnostic ?: codeshield.backend.BackendDiagnosticEngine.diagnoseLocally(dir, stackLabel, exitCode, logs)
                _state.update {
                    it.copy(backendState = it.backendState.copy(diagnostic = diag, isDiagnosing = false))
                }
            }
        }
    }

    /** Exécute une action de remédiation rapide (création DB, lancement conteneur, etc.). */
    fun executeQuickFix(action: codeshield.backend.QuickFixAction) {
        val dir = _state.value.backendState.projectDir ?: _state.value.projectDir
        scope.launch(ioDispatcher) {
            append("[quick-fix] Lancement : ${action.label} (${action.command.joinToString(" ")})")
            var exitCode = 0
            try {
                runner(action.command, dir.ifBlank { null }).collect { line ->
                    when (line) {
                        is TerminalLine.Out -> append(line.text)
                        is TerminalLine.Err -> append("[error] ${line.text}")
                        is TerminalLine.Exit -> exitCode = line.code
                    }
                }
                if (exitCode == 0) {
                    append("[quick-fix] Succès : ${action.label} ✓")
                    if (action.autoRestartBackend) {
                        append("[quick-fix] Redémarrage automatique du backend...")
                        startBackend()
                    }
                } else {
                    append("[quick-fix] [error] Échec : code de sortie $exitCode")
                }
            } catch (e: Exception) {
                append("[quick-fix] [error] Exception : ${e.message}")
            }
        }
    }

    /** Analyse pré-pentest du projet (statique locale / assistée LLM). */
    fun analyzeProject(targetDir: String? = null) {
        val dir = (targetDir ?: _state.value.projectDir).trim()
        if (dir.isBlank() || _state.value.isAnalyzing) return
        val activeId = _state.value.activeTabId
        scope.launch(ioDispatcher) {
            _state.update { curr -> curr.copy(isAnalyzing = true) }
            val analysis = runCatching {
                projectAnalyzer.analyze(dir)
            }.getOrElse { e ->
                codeshield.pentest.ProjectAnalysis(
                    projectDir = dir,
                    notes = listOf("Erreur lors de l'analyse : ${e.message ?: "erreur inconnue"}"),
                )
            }
            _state.update { curr ->
                val updatedTabs = curr.tabs.map { t ->
                    if (t.id == activeId || t.projectDir == dir) t.copy(pentestAnalysis = analysis) else t
                }
                curr.copy(
                    tabs = updatedTabs,
                    pentestAnalysis = if (curr.activeTabId == activeId || curr.projectDir == dir) analysis else curr.pentestAnalysis,
                    isAnalyzing = false,
                )
            }
        }
    }

    fun runPentest() {
        if (_state.value.isPentesting) return
        val url = _state.value.serverUrl
        val port = portFromUrl(url)
        scope.launch(ioDispatcher) {
            _state.update { it.copy(isPentesting = true, pentestProgress = "Analyse du projet...") }
            // 0) Le projet est TOUJOURS analysé avant le pentest : stack, entry points,
            //    routes et sondes alimentent le démarrage ET l'audit.
            val analysis = _state.value.pentestAnalysis
                ?: _state.value.projectDir.ifBlank { null }?.let { dir ->
                    runCatching { projectAnalyzer.analyze(dir) }.getOrNull()
                }
            if (analysis != null) {
                _state.update { curr ->
                    val activeId = curr.activeTabId
                    val updatedTabs = curr.tabs.map { t ->
                        if (t.id == activeId) t.copy(pentestAnalysis = analysis) else t
                    }
                    curr.copy(tabs = updatedTabs, pentestAnalysis = analysis)
                }
            }
            val offline = _state.value.pentestOfflineMode
            if (!offline) {
                _state.update { it.copy(pentestProgress = "Vérification du statut de l'application...") }
                val running = backendRunner.checkHealth(url)
                if (!running) {
                    _state.update { it.copy(pentestProgress = "Application arrêtée. Démarrage automatique (port $port)...") }
                    val customCmd = _state.value.customStartCommand.trim().takeIf { it.isNotBlank() }?.let { listOf("sh", "-c", it) }
                        ?: analysis?.startCommand?.takeIf { it.isNotEmpty() }
                    val startRes = backendRunner.startBackend(_state.value.projectDir.ifBlank { null }, port, customCmd)
                    if (startRes.isFailure) {
                        _state.update {
                            it.copy(
                                isPentesting = false,
                                pentestProgress = null,
                            )
                        }
                        return@launch
                    }
                }
            } else {
                _state.update { it.copy(pentestProgress = "Mode Pentest Direct : audit sans lancement de serveur local...") }
            }

            try {
                val report = pentestEngine.runPentest(url, { msg ->
                    _state.update { it.copy(pentestProgress = msg) }
                }, analysis)
                _state.update {
                    it.copy(
                        isPentesting = false,
                        pentestReport = report,
                        pentestProgress = null,
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        isPentesting = false,
                        pentestProgress = null,
                    )
                }
            }
        }
    }

    private fun append(line: String) {
        // StateFlow est thread-safe : mise à jour directe, Compose recompose.
        _state.update { curr ->
            val newTerminal = (curr.terminal + line).takeLast(1000)
            val activeId = curr.activeTabId
            val updatedTabs = curr.tabs.map { t ->
                if (t.id == activeId) t.copy(terminal = newTerminal) else t
            }
            curr.copy(tabs = updatedTabs, terminal = newTerminal)
        }
    }
}

/**
 * Extrait le port d'une URL serveur (ex. "http://localhost:8001" -> 8001).
 * Retourne 8000 par défaut. Permet de lancer le backend isolé pour pentest
 * sur un autre port sans conflit avec l'instance dev.
 */
internal fun portFromUrl(url: String): Int = runCatching {
    val afterScheme = url.substringAfter("://", url)
    val hostPort = afterScheme.substringBefore("/")
    val portPart = hostPort.substringAfter(":", "")
    portPart.toIntOrNull()?.takeIf { it in 1..65535 } ?: 8000
}.getOrDefault(8000)
