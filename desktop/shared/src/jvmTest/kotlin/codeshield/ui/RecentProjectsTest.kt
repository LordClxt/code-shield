package codeshield.ui

import codeshield.cache.InMemoryHistoryStore
import codeshield.cache.PersistedScan
import codeshield.llm.InMemorySettingsStore
import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import codeshield.tools.ToolInstaller
import codeshield.tools.ToolState
import codeshield.tools.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentProjectsTest {

    private fun dummyInstaller() = object : ToolInstaller {
        override fun installedPath(tool: ToolName): String? = null
        override suspend fun status(tool: ToolName): ToolStatus = ToolStatus(tool, ToolState.READY)
        override suspend fun install(tool: ToolName, onProgress: (Float) -> Unit): Result<String> = Result.success("/bin/dummy")
    }

    @Test
    fun testConsultedProjectNameFallback() {
        val p1 = ConsultedProject("/Users/dev/acme-api", findingsCount = 5)
        assertEquals("acme-api", p1.name)
        assertEquals(5, p1.findingsCount)

        val p2 = ConsultedProject("C:\\Projects\\MyApp\\", findingsCount = 0)
        assertEquals("MyApp", p2.name)
    }

    @Test
    fun testOpenProjectUpdatesRecentsAndRestoresScan() = runTest {
        val historyStore = InMemoryHistoryStore()
        val settingsStore = InMemorySettingsStore()

        // Pré-charge un scan pour /test/project-a
        val dummyFinding = Finding(
            filePath = "src/Main.kt",
            lineNumber = 10,
            ruleId = "rule.security.test",
            severity = Severity.HIGH,
            title = "Test issue",
            tool = ToolName.SEMGREP,
        )
        historyStore.save(
            PersistedScan(
                projectDir = "/test/project-a",
                findings = listOf(dummyFinding),
                terminal = listOf("scan a done"),
                finishedAtEpochMs = 123456789L,
            ),
        )

        val vm = ScannerViewModel(
            scope = this,
            runner = { _, _ -> emptyFlow() },
            fileReader = { emptyList() },
            syncFactory = { _, _ -> error("unused") },
            locator = { null },
            dirChecker = { true },
            installer = dummyInstaller(),
            historyStore = historyStore,
            settingsStore = settingsStore,
            ioDispatcher = Dispatchers.Unconfined,
        )

        // 1. Ouvre project-a
        vm.openProject("/test/project-a")
        testScheduler.advanceUntilIdle()

        assertEquals("/test/project-a", vm.state.value.projectDir)
        assertEquals(listOf("/test/project-a"), vm.state.value.llmSettings.recentProjects)
        assertEquals(1, vm.state.value.findings.size)
        assertEquals("Test issue", vm.state.value.findings.first().title)

        // 2. Ouvre un nouveau projet sans scan préalable
        vm.openProject("/test/project-b")
        testScheduler.advanceUntilIdle()

        assertEquals("/test/project-b", vm.state.value.projectDir)
        assertEquals(listOf("/test/project-b", "/test/project-a"), vm.state.value.llmSettings.recentProjects)
        assertTrue(vm.state.value.findings.isEmpty())

        // 3. Re-sélectionne project-a : restaure les findings et repasse en tête de liste
        vm.openProject("/test/project-a")
        testScheduler.advanceUntilIdle()

        assertEquals("/test/project-a", vm.state.value.projectDir)
        assertEquals(listOf("/test/project-a", "/test/project-b"), vm.state.value.llmSettings.recentProjects)
        assertEquals(1, vm.state.value.findings.size)

        // 4. Clear recents
        vm.clearRecentProjects()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.llmSettings.recentProjects.isEmpty())
    }
}
