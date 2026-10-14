package codeshield.scanner

import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScannerEngineTest {

    @Test
    fun everyActiveToolHasAnEngine() {
        // Tous les outils du contrat ont un moteur, sauf les identifiants historiques.
        val retired = setOf(ToolName.BANDIT, ToolName.CHECKOV)
        assertEquals(ToolName.entries.toSet() - retired, ScannerEngines.all.map { it.tool }.toSet())
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
    fun opengrepUsesConfigWithoutMetricsFlag() {
        val cmd = OpengrepEngine.command("opengrep", "/w", EngineOptions(semgrepConfig = "p/owasp-top-ten"))
        // Opengrep n'a pas d'option --metrics : la passer le ferait échouer (code 2).
        assertTrue(cmd.none { it.startsWith("--metrics") })
        assertTrue("p/owasp-top-ten" in cmd)
        assertTrue("--json" in cmd)
    }

    @Test
    fun opengrepKeepsSemgrepIdentityInContract() {
        // Même outil côté contrat : les failles déjà synchronisées gardent leur identité.
        assertEquals(ToolName.SEMGREP, OpengrepEngine.tool)
        assertEquals("opengrep", OpengrepEngine.binary)
    }

    @Test
    fun retiredPythonEnginesAreGone() {
        // Bandit et Checkov retirés : identifiants gardés pour l'historique, plus aucun moteur.
        assertNull(ScannerEngines.ofOrNull(ToolName.BANDIT))
        assertNull(ScannerEngines.ofOrNull(ToolName.CHECKOV))
        assertTrue(ScannerEngines.defaultEnabled.containsAll(setOf(ToolName.SEMGREP, ToolName.GITLEAKS, ToolName.TRIVY)))
    }

    @Test
    fun opengrepNeverUsesAutoConfig() {
        for (opts in listOf(EngineOptions(), EngineOptions(semgrepConfig = "auto"))) {
            val cmd = OpengrepEngine.command("opengrep", "/w", opts)
            assertTrue("auto" !in cmd)
            assertTrue(DEFAULT_SEMGREP_CONFIG in cmd)
        }
    }

    @Test
    fun exitCodesFollowEachToolConvention() {
        assertTrue(OpengrepEngine.isExpectedExit(1))
        assertFalse(OpengrepEngine.isExpectedExit(2))
        assertTrue(OsvScannerEngine.isExpectedExit(128))
        assertFalse(DependencyCheckEngine.isExpectedExit(1))
    }

    @Test
    fun autoDetectionSkipsEnginesWithoutTargets() {
        val jsOnly = ProjectProfile(extensions = setOf("ts", "json"), fileNames = setOf("package.json", "index.ts"))
        val config = ScannerConfig(projectDir = "/p", enabled = ToolName.entries.toSet())
        val tools = config.enginesFor(jsOnly).map { it.tool }
        assertFalse(ToolName.OSV_SCANNER in tools, "pas de lockfile")
        assertTrue(ToolName.SEMGREP in tools && ToolName.GITLEAKS in tools && ToolName.TRIVY in tools)

        val withLock = jsOnly.copy(fileNames = jsOnly.fileNames + "package-lock.json")
        assertTrue(ToolName.OSV_SCANNER in config.enginesFor(withLock).map { it.tool })
        assertEquals(ScannerEngines.all.size, config.copy(autoDetect = false).enginesFor(jsOnly).size)
        assertEquals(ScannerEngines.all.size, config.enginesFor(ProjectProfile.UNKNOWN).size)
    }

    @Test
    fun disabledEnginesNeverRun() {
        val config = ScannerConfig(projectDir = "/p", enabled = setOf(ToolName.GITLEAKS))
        assertEquals(listOf(ToolName.GITLEAKS), config.enginesFor(ProjectProfile.UNKNOWN).map { it.tool })
    }
}
