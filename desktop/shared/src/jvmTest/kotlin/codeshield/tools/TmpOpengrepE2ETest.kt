package codeshield.tools

import codeshield.model.ToolName
import codeshield.scanner.EngineOptions
import codeshield.scanner.OpengrepEngine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TmpOpengrepE2ETest {
    @Test
    fun installAndScan() = kotlinx.coroutines.test.runTest(timeout = kotlin.time.Duration.parse("5m")) {
        val project = System.getenv("E2E_PROJECT") ?: return@runTest
        val dir = File(System.getenv("E2E_TOOLS"))
        val installer = JvmToolInstaller(toolsDir = dir)
        val path = installer.install(ToolName.SEMGREP) {}.getOrThrow()
        println("installed: $path")
        val cmd = OpengrepEngine.command(path, "/tmp", EngineOptions())
        val proc = ProcessBuilder(cmd).directory(File(project)).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        val findings = OpengrepEngine.parse(out, project)
        println("exit=$code findings=${findings.size}")
        findings.forEach { println("  ${it.severity} ${it.ruleId.substringAfterLast('.')} ${it.filePath}:${it.lineNumber} ${it.cwe}") }
        assertTrue(OpengrepEngine.isExpectedExit(code) && findings.isNotEmpty())
    }
}
