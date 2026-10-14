package codeshield.ui

import codeshield.model.ScanPayload
import codeshield.model.Severity
import codeshield.model.ToolName
import codeshield.sync.SyncClient
import codeshield.sync.SyncResponse
import codeshield.tools.ToolInstaller
import codeshield.tools.ToolState
import codeshield.tools.ToolStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val neverSync: SyncClientFactory = { _, _ ->
    object : SyncClient {
        override suspend fun submit(payload: ScanPayload): SyncResponse = error("no network in tests")
    }
}

/** Aucun outil géré : le pré-vol repose uniquement sur le PATH système (fake). */
private object NoManagedTools : ToolInstaller {
    override fun installedPath(tool: codeshield.model.ToolName): String? = null
    override suspend fun status(tool: codeshield.model.ToolName): ToolStatus =
        ToolStatus(tool, ToolState.MISSING)
    override suspend fun install(tool: codeshield.model.ToolName, onProgress: (Float) -> Unit): Result<String> =
        Result.failure(UnsupportedOperationException("no managed install in this test"))
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private fun TestScope.viewModel(
        runner: CommandRunner = { _, _ -> flow {} },
        locator: BinaryLocator = { "/usr/bin/$it" },
        dirChecker: DirChecker = { true },
        installer: ToolInstaller = NoManagedTools,
        inputRunner: InputCommandRunner = { cmd, dir, _ -> runner(cmd, dir) },
    ) = ScannerViewModel(
        scope = this,
        runner = runner,
        fileReader = { emptyList() },
        syncFactory = neverSync,
        locator = locator,
        dirChecker = dirChecker,
        installer = installer,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        inputRunner = inputRunner,
    )

    @Test
    fun missingDirectoryAbortsWithInlineError() = runTest {
        val vm = viewModel(dirChecker = { false })
        vm.update { it.copy(projectDir = "/nonexistent") }
        vm.runScan()
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isScanning)
        assertTrue(state.terminal.any { it.contains("Dossier introuvable") }, state.terminal.toString())
        assertTrue(state.findings.isEmpty())
    }

    @Test
    fun missingBinariesAreSkippedWithInstallHints() = runTest {
        var runnerCalled = false
        val vm = viewModel(
            runner = { _, _ -> runnerCalled = true; flow {} },
            locator = { null },
        )
        vm.update { it.copy(projectDir = "/proj") }
        vm.runScan()
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isScanning)
        assertFalse(runnerCalled, "aucun processus ne doit être lancé sans binaire")
        assertTrue(state.terminal.any { it.contains("Download") }, state.terminal.toString())
    }

    @Test
    fun runnerExceptionBecomesTerminalErrorInsteadOfCrash() = runTest {
        val vm = viewModel(
            runner = { _, _ -> flow { throw IllegalStateException("exec failed, error=2") } },
        )
        vm.update { it.copy(projectDir = "/proj") }
        vm.runScan() // ne doit jamais lever (le crash natif venait d'ici)
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isScanning)
        assertTrue(state.terminal.any { it.startsWith("[error]") }, state.terminal.toString())
    }

    @Test
    fun enginesRunInParallelAndParseStdoutAndReportFiles() = runTest {
        val semgrepJson = """{"results":[{"check_id":"r.sqli","path":"./app/db.py","start":{"line":3},"extra":{"severity":"ERROR","message":"m"}}]}"""
        val gitleaksJson = """[{"RuleID":"aws-access-token","File":"app/.env","StartLine":1}]"""
        val commands = mutableListOf<Pair<List<String>, String?>>()
        val vm = ScannerViewModel(
            scope = this,
            runner = { cmd, dir ->
                commands += cmd to dir
                flow {
                    if (cmd.first().endsWith("semgrep")) emit(TerminalLine.Out(semgrepJson))
                    emit(TerminalLine.Exit(cmd.first(), 1))
                }
            },
            fileReader = { emptyList() },
            syncFactory = neverSync,
            locator = { "/usr/bin/$it" },
            dirChecker = { true },
            installer = NoManagedTools,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            workDirs = ScanWorkDirs(create = { "/tmp/w" }),
            reportReader = { path -> if (path == "/tmp/w/gitleaks.json") listOf(gitleaksJson) else emptyList() },
        )
        vm.update { it.copy(projectDir = "/proj", enabledEngines = setOf(ToolName.SEMGREP, ToolName.GITLEAKS)) }
        vm.runScan()
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isScanning)
        assertEquals(2, state.findings.size, state.terminal.joinToString("\n"))
        assertEquals(Severity.CRITICAL, state.findings.first().severity, "trié par sévérité")
        assertEquals("app/db.py", state.findings.first { it.tool == ToolName.SEMGREP }.filePath)
        assertTrue(commands.all { it.second == "/proj" }, "cwd = racine du projet")
        assertEquals(EngineState.DONE, state.engineRuns[ToolName.SEMGREP]?.state)
        assertEquals(1, state.engineRuns[ToolName.GITLEAKS]?.findings)
        assertFalse(state.terminal.any { it.contains("\"results\"") }, "le JSON brut n'encombre pas le terminal")
    }

    @Test
    fun cancelKillsRunningEngines() = runTest {
        val vm = viewModel(runner = { _, _ -> flow { awaitCancellation() } })
        vm.update { it.copy(projectDir = "/proj", enabledEngines = setOf(ToolName.SEMGREP)) }
        vm.runScan()
        runCurrent()
        assertTrue(vm.state.value.isScanning)
        vm.cancelScan()
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isScanning)
        assertEquals(EngineState.CANCELLED, state.engineRuns[ToolName.SEMGREP]?.state)
        assertTrue(state.findings.isEmpty())
    }

    @Test
    fun unexpectedExitCodeMarksEngineFailed() = runTest {
        val vm = viewModel(runner = { cmd, _ -> flow { emit(TerminalLine.Exit(cmd.first(), 2)) } })
        vm.update { it.copy(projectDir = "/proj", enabledEngines = setOf(ToolName.SEMGREP)) }
        vm.runScan()
        advanceUntilIdle()
        assertEquals(EngineState.FAILED, vm.state.value.engineRuns[ToolName.SEMGREP]?.state)
        assertTrue(vm.state.value.terminal.any { it.startsWith("[error]") && it.contains("code 2") })
    }

    @Test
    fun selectReadsFilesRelativeToProjectAndReportsErrors() = runTest {
        val vm = ScannerViewModel(
            scope = this, runner = { _, _ -> flow {} },
            fileReader = { path -> if (path == "/proj/app/db.py") listOf("x") else error("Fichier introuvable : $path") },
            syncFactory = neverSync, locator = { null }, dirChecker = { true }, installer = NoManagedTools,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        vm.update { it.copy(projectDir = "/proj") }
        vm.select(codeshield.model.Finding(ToolName.SEMGREP, "r", "app/db.py", 1, Severity.LOW, "t"))
        advanceUntilIdle()
        assertEquals(listOf("x"), vm.state.value.fileLines)
        vm.select(codeshield.model.Finding(ToolName.SEMGREP, "r", "gone.py", 1, Severity.LOW, "t"))
        advanceUntilIdle()
        assertTrue(vm.state.value.fileError?.contains("introuvable") == true, vm.state.value.fileError)
    }

    @Test
    fun syncWithoutCredentialsExplainsInline() = runTest {
        val vm = viewModel()
        vm.sync() // synchrone : validation avant tout lancement
        assertTrue(vm.state.value.syncMessage?.contains("projectId") == true)
    }

    private val claudeFinding = codeshield.model.Finding(
        tool = ToolName.SEMGREP, ruleId = "r1", filePath = "app/run.py", lineNumber = 3,
        severity = Severity.HIGH, title = "shell=True",
    )

    @Test
    fun fixWithClaudeCodeRunsInProjectDirAndKeepsSummary() = runTest {
        var seen: Pair<List<String>, String?>? = null
        val vm = viewModel(runner = { cmd, dir ->
            seen = cmd to dir
            flow {
                emit(TerminalLine.Out("""{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Edit","input":{"file_path":"app/run.py"}}]}}"""))
                emit(TerminalLine.Out("""{"type":"result","subtype":"success","is_error":false,"result":"shell=False appliqué."}"""))
                emit(TerminalLine.Exit("claude", 0))
            }
        })
        vm.update { it.copy(projectDir = "/proj") }
        vm.fixWithClaudeCode(claudeFinding)
        advanceUntilIdle()
        val (cmd, dir) = seen!!
        assertEquals("/usr/bin/claude", cmd.first())
        assertEquals("/proj", dir)
        val fix = vm.state.value.claudeFix!!
        assertFalse(fix.running)
        assertEquals(true, fix.success)
        assertEquals("shell=False appliqué.", fix.summary)
        assertEquals(listOf("→ Edit app/run.py"), fix.log)
    }

    @Test
    fun fixWithClaudeCodeReportsMissingBinary() = runTest {
        val vm = viewModel(locator = { null })
        vm.update { it.copy(projectDir = "/proj") }
        vm.fixWithClaudeCode(claudeFinding)
        advanceUntilIdle()
        val fix = vm.state.value.claudeFix!!
        assertEquals(false, fix.success)
        assertTrue(fix.summary!!.contains("introuvable"), fix.summary)
    }

    @Test
    fun fixCriticalAndHighSendsOneSessionWithOnlyUrgentFindings() = runTest {
        var prompt: String? = null
        val vm = viewModel(inputRunner = { _, _, stdin ->
            prompt = stdin
            flow {
                emit(TerminalLine.Out("""{"type":"result","subtype":"success","is_error":false,"result":"2 corrigées."}"""))
                emit(TerminalLine.Exit("claude", 0))
            }
        })
        val critical = claudeFinding.copy(severity = Severity.CRITICAL, filePath = "db.py")
        val low = claudeFinding.copy(severity = Severity.LOW, filePath = "style.css")
        vm.update { it.copy(projectDir = "/proj", findings = listOf(low, claudeFinding, critical)) }
        vm.fixCriticalAndHighWithClaudeCode()
        advanceUntilIdle()
        val fix = vm.state.value.claudeFix!!
        assertEquals(listOf(critical, claudeFinding), fix.findings)
        assertEquals(true, fix.success)
        assertTrue("db.py" in prompt!! && "style.css" !in prompt!!, prompt)
        vm.dismissClaudeFix()
        assertEquals(null, vm.state.value.claudeFix)
    }
}
