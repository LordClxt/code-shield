package codeshield.scanner

import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannerEngineTest {

    @Test
    fun everyToolHasAnEngine() {
        assertEquals(ToolName.entries.toSet(), ScannerEngines.all.map { it.tool }.toSet())
    }

    @Test
    fun enginesTargetTheProjectRootAndWriteReportsOutsideIt() {
        for (engine in ScannerEngines.all) {
            val cmd = engine.command("/bin/${engine.binary}", "/tmp/work", EngineOptions())
            assertEquals("/bin/${engine.binary}", cmd.first())
            assertTrue("." in cmd, "${engine.label} doit cibler `.` (cwd = projet) : $cmd")
            if (engine.outputMode == OutputMode.REPORT_FILE) {
                assertTrue(engine.reportPath("/tmp/work").startsWith("/tmp/work/"), engine.label)
            }
        }
    }

    @Test
    fun gitleaksRedactsSecrets() {
        assertTrue("--redact" in GitleaksEngine.command("gitleaks", "/w", EngineOptions()))
    }

    @Test
    fun semgrepDisablesMetricsAndUsesConfig() {
        val cmd = SemgrepEngine.command("semgrep", "/w", EngineOptions(semgrepConfig = "p/owasp-top-ten"))
        assertTrue("--metrics=off" in cmd)
        assertTrue("p/owasp-top-ten" in cmd)
        assertTrue("--json" in cmd)
    }

    @Test
    fun semgrepNeverUsesAutoConfigWithMetricsOff() {
        // `--config auto` + `--metrics=off` → semgrep sort en code 2.
        for (opts in listOf(EngineOptions(), EngineOptions(semgrepConfig = "auto"))) {
            val cmd = SemgrepEngine.command("semgrep", "/w", opts)
            assertTrue("auto" !in cmd)
            assertTrue(DEFAULT_SEMGREP_CONFIG in cmd)
        }
    }

    @Test
    fun exitCodesFollowEachToolConvention() {
        assertTrue(SemgrepEngine.isExpectedExit(1))
        assertFalse(SemgrepEngine.isExpectedExit(2))
        assertTrue(OsvScannerEngine.isExpectedExit(128))
        assertFalse(DependencyCheckEngine.isExpectedExit(1))
    }

    @Test
    fun autoDetectionSkipsEnginesWithoutTargets() {
        val jsOnly = ProjectProfile(extensions = setOf("ts", "json"), fileNames = setOf("package.json", "index.ts"))
        val config = ScannerConfig(projectDir = "/p", enabled = ToolName.entries.toSet())
        val tools = config.enginesFor(jsOnly).map { it.tool }
        assertFalse(ToolName.BANDIT in tools, "pas de Python")
        assertFalse(ToolName.CHECKOV in tools, "pas d'IaC")
        assertFalse(ToolName.OSV_SCANNER in tools, "pas de lockfile")
        assertTrue(ToolName.SEMGREP in tools && ToolName.GITLEAKS in tools && ToolName.TRIVY in tools)

        val withLock = jsOnly.copy(fileNames = jsOnly.fileNames + "package-lock.json")
        assertTrue(ToolName.OSV_SCANNER in config.enginesFor(withLock).map { it.tool })
        assertEquals(7, config.copy(autoDetect = false).enginesFor(jsOnly).size)
        assertEquals(7, config.enginesFor(ProjectProfile.UNKNOWN).size)
    }

    @Test
    fun disabledEnginesNeverRun() {
        val config = ScannerConfig(projectDir = "/p", enabled = setOf(ToolName.GITLEAKS))
        assertEquals(listOf(ToolName.GITLEAKS), config.enginesFor(ProjectProfile.UNKNOWN).map { it.tool })
    }
}
