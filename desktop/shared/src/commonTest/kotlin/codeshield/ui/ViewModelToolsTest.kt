package codeshield.ui

import codeshield.model.ToolName
import codeshield.tools.ToolInstaller
import codeshield.tools.ToolState
import codeshield.tools.ToolStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeInstaller(
    val installed: MutableMap<ToolName, String> = mutableMapOf(),
    var installResult: Result<String> = Result.success("/managed/gitleaks"),
) : ToolInstaller {
    val progressSeen = mutableListOf<Float>()
    override fun installedPath(tool: ToolName): String? = installed[tool]
    override suspend fun status(tool: ToolName): ToolStatus {
        val path = installed[tool]
        return if (path != null) ToolStatus(tool, ToolState.READY, path = path)
        else ToolStatus(tool, ToolState.MISSING)
    }
    override suspend fun install(tool: ToolName, onProgress: (Float) -> Unit): Result<String> {
        onProgress(0.5f).also { progressSeen.add(0.5f) }
        return installResult.also { r -> r.onSuccess { installed[tool] = it } }
    }
}

private fun TestScope.vmWith(installer: FakeInstaller, locator: BinaryLocator = { null }) =
    ScannerViewModel(
        scope = this,
        runner = { _, _ -> flow {} },
        fileReader = { emptyList() },
        syncFactory = { _, _ -> error("no network") },
        locator = locator,
        dirChecker = { true },
        installer = installer,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelToolsTest {

    @Test
    fun refreshToolsMarksManagedAndSystemBinariesReady() = runTest {
        val installer = FakeInstaller(installed = mutableMapOf(ToolName.GITLEAKS to "/managed/gitleaks"))
        val vm = vmWith(installer, locator = { if (it == "semgrep") "/usr/bin/semgrep" else null })
        vm.refreshTools()
        advanceUntilIdle()
        val statuses = vm.state.value.toolStatuses
        assertEquals(ToolState.READY, statuses[ToolName.GITLEAKS]?.state)
        assertEquals("/managed/gitleaks", statuses[ToolName.GITLEAKS]?.path)
        assertEquals(ToolState.READY, statuses[ToolName.SEMGREP]?.state)
        assertEquals(ToolState.MISSING, statuses[ToolName.DEPENDENCY_CHECK]?.state)
    }

    @Test
    fun installToolSuccessUpdatesStatusAndTerminal() = runTest {
        val installer = FakeInstaller()
        val vm = vmWith(installer)
        vm.installTool(ToolName.GITLEAKS)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(ToolState.READY, state.toolStatuses[ToolName.GITLEAKS]?.state)
        assertTrue(state.terminal.any { it.startsWith("Installed gitleaks") }, state.terminal.toString())
        assertTrue(installer.progressSeen.isNotEmpty())
    }

    @Test
    fun installToolFailureStaysMissingWithError() = runTest {
        val installer = FakeInstaller(installResult = Result.failure(IllegalStateException("HTTP 404")))
        val vm = vmWith(installer)
        vm.installTool(ToolName.GITLEAKS)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(ToolState.MISSING, state.toolStatuses[ToolName.GITLEAKS]?.state)
        assertEquals("HTTP 404", state.toolStatuses[ToolName.GITLEAKS]?.error)
        assertTrue(state.terminal.any { it.contains("échouée") }, state.terminal.toString())
    }

    @Test
    fun managedBinaryIsPreferredOverSystemOne() = runTest {
        val installer = FakeInstaller(installed = mutableMapOf(ToolName.SEMGREP to "/managed/semgrep"))
        val vm = vmWith(installer, locator = { "/usr/bin/semgrep" })
        vm.refreshTools()
        advanceUntilIdle()
        assertEquals("/managed/semgrep", vm.state.value.toolStatuses[ToolName.SEMGREP]?.path)
    }
}
