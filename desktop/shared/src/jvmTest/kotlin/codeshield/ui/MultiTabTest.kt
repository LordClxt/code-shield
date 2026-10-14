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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MultiTabTest {

    private fun dummyInstaller() = object : ToolInstaller {
        override fun installedPath(tool: ToolName): String? = null
        override suspend fun status(tool: ToolName): ToolStatus = ToolStatus(tool, ToolState.READY)
        override suspend fun install(tool: ToolName, onProgress: (Float) -> Unit): Result<String> = Result.success("/bin/dummy")
    }

    private fun createViewModel(historyStore: InMemoryHistoryStore = InMemoryHistoryStore()): ScannerViewModel {
        return ScannerViewModel(
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            runner = { _, _ -> emptyFlow() },
            fileReader = { emptyList() },
            syncFactory = { _, _ ->
                object : codeshield.sync.SyncClient {
                    override suspend fun submit(payload: codeshield.model.ScanPayload): codeshield.sync.SyncResponse =
                        error("no network in tests")
                }
            },
            locator = { null },
            dirChecker = { true },
            installer = dummyInstaller(),
            ioDispatcher = Dispatchers.Unconfined,
            historyStore = historyStore,
            settingsStore = InMemorySettingsStore(),
        )
    }

    @Test
    fun testInitialStateHasSingleTab() {
        val vm = createViewModel()
        val state = vm.state.value

        assertEquals(1, state.tabs.size)
        assertEquals(state.activeTabId, state.tabs.first().id)
        assertEquals("Untitled Project", state.activeTab.title)
    }

    @Test
    fun testCreateTabAndSelect() {
        val vm = createViewModel()
        val tab1Id = vm.state.value.activeTabId

        val tab2Id = vm.createTab("/projects/auth-service", activate = true)
        assertEquals(2, vm.state.value.tabs.size)
        assertEquals(tab2Id, vm.state.value.activeTabId)
        assertEquals("auth-service", vm.state.value.activeTab.title)
        assertEquals("/projects/auth-service", vm.state.value.projectDir)

        // Bascule vers le premier onglet
        vm.selectTab(tab1Id)
        assertEquals(tab1Id, vm.state.value.activeTabId)
        assertEquals("", vm.state.value.projectDir)
    }

    @Test
    fun testCloseTabSwitchesToNeighbor() {
        val vm = createViewModel()
        val t1 = vm.state.value.tabs[0].id
        val t2 = vm.createTab("/project-2", activate = true)
        val t3 = vm.createTab("/project-3", activate = true)

        assertEquals(3, vm.state.value.tabs.size)
        assertEquals(t3, vm.state.value.activeTabId)

        // Fermeture de l'onglet actif t3 -> bascule automatique sur t2
        vm.closeTab(t3)
        assertEquals(2, vm.state.value.tabs.size)
        assertEquals(t2, vm.state.value.activeTabId)
        assertEquals("/project-2", vm.state.value.projectDir)

        // Fermeture de t2 -> bascule sur t1
        vm.closeTab(t2)
        assertEquals(1, vm.state.value.tabs.size)
        assertEquals(t1, vm.state.value.activeTabId)

        // Fermeture du dernier onglet restant -> recrée un onglet vierge
        vm.closeTab(t1)
        assertEquals(1, vm.state.value.tabs.size)
        assertNotEquals(t1, vm.state.value.activeTabId)
        assertEquals("", vm.state.value.projectDir)
    }

    @Test
    fun testOpenProjectDeduplication() {
        val vm = createViewModel()

        vm.openProject("/workspaces/backend", inNewTab = true)
        val backendTabId = vm.state.value.activeTabId

        vm.openProject("/workspaces/frontend", inNewTab = true)
        val frontendTabId = vm.state.value.activeTabId
        assertNotEquals(backendTabId, frontendTabId)
        assertEquals(3, vm.state.value.tabs.size) // 1 initial + backend + frontend

        // Tenter d'ouvrir /workspaces/backend à nouveau : doit réactiver l'onglet existant sans doublon
        vm.openProject("/workspaces/backend")
        assertEquals(backendTabId, vm.state.value.activeTabId)
        assertEquals(3, vm.state.value.tabs.size)
    }

    @Test
    fun testTabPreservesIsolatedFindings() = runTest {
        val historyStore = InMemoryHistoryStore()
        val finding = Finding(
            filePath = "App.kt",
            lineNumber = 42,
            ruleId = "rule.leak",
            severity = Severity.CRITICAL,
            title = "Secret leak",
            tool = ToolName.GITLEAKS,
        )
        historyStore.save(
            PersistedScan(
                projectDir = "/workspaces/service-a",
                findings = listOf(finding),
                terminal = listOf("scan complete"),
                finishedAtEpochMs = 123456L,
            ),
        )

        val vm = createViewModel(historyStore)

        // Ouvre service-a dans tab 1
        vm.openProject("/workspaces/service-a")
        assertEquals(1, vm.state.value.findings.size)
        val tab1Id = vm.state.value.activeTabId

        // Ouvre service-b dans un nouvel onglet
        val tab2Id = vm.createTab("/workspaces/service-b", activate = true)
        assertEquals(0, vm.state.value.findings.size)

        // Retour sur tab 1
        vm.selectTab(tab1Id)
        assertEquals(1, vm.state.value.findings.size)
        assertEquals("Secret leak", vm.state.value.findings.first().title)
    }
}
