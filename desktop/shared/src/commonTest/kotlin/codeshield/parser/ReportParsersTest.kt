package codeshield.parser

import codeshield.model.Severity
import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportParsersTest {

    @Test
    fun parsesSemgrepJson() {
        val raw = """
        {
          "results": [
            {
              "check_id": "python.lang.security.audit.dangerous-exec",
              "path": "src/app.py",
              "start": {"line": 42},
              "extra": {"severity": "ERROR", "message": "Avoid exec"}
            },
            {
              "check_id": "python.flask.security.injection",
              "path": "src/web.py",
              "start": {"line": 7},
              "extra": {"severity": "WARNING", "message": "SSTI risk"}
            }
          ]
        }
        """.trimIndent()
        val findings = parseSemgrepResults(raw)
        assertEquals(2, findings.size)
        assertEquals(ToolName.SEMGREP, findings[0].tool)
        assertEquals("src/app.py", findings[0].filePath)
        assertEquals(42, findings[0].lineNumber)
        assertEquals(Severity.CRITICAL, findings[0].severity)
        assertEquals(Severity.HIGH, findings[1].severity)
    }

    @Test
    fun parsesGitleaksJsonAsHigh() {
        val raw = """
        [
          {"RuleID": "github-pat", "File": "src/config.py", "StartLine": 12,
           "Description": "GitHub token"}
        ]
        """.trimIndent()
        val findings = parseGitleaksResults(raw)
        assertEquals(1, findings.size)
        assertEquals(ToolName.GITLEAKS, findings[0].tool)
        assertEquals("github-pat", findings[0].ruleId)
        assertEquals(Severity.HIGH, findings[0].severity)
    }

    @Test
    fun invalidJsonReturnsEmptyInsteadOfThrowing() {
        assertTrue(parseSemgrepResults("not json").isEmpty())
        assertTrue(parseGitleaksResults("{oops").isEmpty())
        assertTrue(parseSemgrepResults("""{"results": "nope"}""").isEmpty())
    }

    @Test
    fun incompleteEntriesAreSkipped() {
        val raw = """{"results": [{"check_id": "R", "start": {"line": 1}}]}"""
        assertTrue(parseSemgrepResults(raw).isEmpty(), "entrée sans path ignorée")
    }
}
