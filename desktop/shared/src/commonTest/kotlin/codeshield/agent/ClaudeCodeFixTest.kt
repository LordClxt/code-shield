package codeshield.agent

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaudeCodeFixTest {

    private val finding = Finding(
        tool = ToolName.SEMGREP,
        ruleId = "python.lang.security.audit.subprocess-shell-true",
        filePath = "app/run.py",
        lineNumber = 12,
        endLine = 14,
        severity = Severity.HIGH,
        title = "subprocess with shell=True",
        message = "Found subprocess call with shell=True",
        cwe = listOf("CWE-78"),
    )

    @Test
    fun promptCarriesMetadataAndInstructions() {
        val p = ClaudeCodeFix.prompt(finding)
        assertTrue("app/run.py (lignes 12-14)" in p, p)
        assertTrue("SEMGREP / python.lang.security.audit.subprocess-shell-true" in p, p)
        assertTrue("CWE-78" in p, p)
        assertTrue("faux positif" in p, p)
    }

    @Test
    fun commandIsHeadlessWithoutBash() {
        val cmd = ClaudeCodeFix.command("/bin/claude")
        assertEquals(listOf("/bin/claude", "-p"), cmd.take(2))
        assertTrue(cmd.containsAll(listOf("--permission-mode", "acceptEdits", "--output-format", "stream-json", "--verbose")))
        val tools = cmd[cmd.indexOf("--allowedTools") + 1]
        assertTrue("Edit" in tools && "Read" in tools)
        assertFalse("Bash" in tools)
    }

    @Test
    fun parsesAssistantTextAndToolUse() {
        val line = """{"type":"assistant","message":{"content":[{"type":"text","text":"Je lis le fichier."},""" +
            """{"type":"tool_use","name":"Edit","input":{"file_path":"/p/app/run.py"}}]}}"""
        assertEquals(
            listOf(ClaudeEvent.Text("Je lis le fichier."), ClaudeEvent.ToolUse("Edit", "/p/app/run.py")),
            parseClaudeStreamLine(line),
        )
    }

    @Test
    fun parsesResultAndIgnoresNoise() {
        assertEquals(
            listOf(ClaudeEvent.Result("Corrigé.", isError = false)),
            parseClaudeStreamLine("""{"type":"result","subtype":"success","is_error":false,"result":"Corrigé."}"""),
        )
        assertEquals(
            listOf(ClaudeEvent.Result("", isError = true)),
            parseClaudeStreamLine("""{"type":"result","subtype":"error_max_turns","is_error":true}"""),
        )
        assertTrue(parseClaudeStreamLine("""{"type":"system","subtype":"init"}""").isEmpty())
        assertTrue(parseClaudeStreamLine("not json").isEmpty())
    }

    @Test
    fun batchKeepsCriticalAndHighOnlyMostSevereFirst() {
        val low = finding.copy(severity = Severity.LOW, filePath = "a.py")
        val critical = finding.copy(severity = Severity.CRITICAL, filePath = "z.py")
        val dup = finding.copy(tool = ToolName.BANDIT)
        assertEquals(listOf(critical, finding), ClaudeCodeFix.batchTargets(listOf(low, finding, dup, critical)))
        val many = (1..40).map { finding.copy(lineNumber = it, endLine = null) }
        assertEquals(ClaudeCodeFix.MAX_BATCH, ClaudeCodeFix.batchTargets(many).size)
    }

    @Test
    fun batchPromptNumbersEachFinding() {
        val p = ClaudeCodeFix.prompt(listOf(finding, finding.copy(filePath = "b.py", lineNumber = 2, endLine = null)))
        assertTrue("Corrige ces 2 failles" in p, p)
        assertTrue("1. HIGH" in p && "2. HIGH" in p, p)
        assertTrue("b.py (lignes 2)" in p, p)
        assertTrue("par faille" in p, p)
    }
}
