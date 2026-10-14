package codeshield.logic

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertTrue

class IssueGuideTest {
    private fun finding(tool: ToolName, rule: String) = Finding(tool, rule, "a.py", 1, Severity.HIGH, "t")

    @Test
    fun semgrepExecExplainsRce() {
        val e = explainFinding(finding(ToolName.SEMGREP, "python.lang.security.audit.dangerous-exec"))
        assertTrue(e.category.contains("commande", ignoreCase = true), e.category)
        assertTrue(e.fix.contains("exec", ignoreCase = true), e.fix)
    }

    @Test
    fun gitleaksExplainsRevocation() {
        val e = explainFinding(finding(ToolName.GITLEAKS, "github-pat"))
        assertTrue(e.category.contains("Secret", ignoreCase = true), e.category)
        assertTrue(e.fix.contains("Révoquez", ignoreCase = true), e.fix)
    }

    @Test
    fun unknownRuleStillExplains() {
        val e = explainFinding(finding(ToolName.SEMGREP, "custom.rule.unknown"))
        assertTrue(e.what.isNotBlank() && e.why.isNotBlank() && e.fix.isNotBlank())
    }

    @Test
    fun severityBlurbMentionsPriority() {
        assertTrue(severityBlurb(Severity.CRITICAL).contains("priorité", ignoreCase = true))
        assertTrue(severityBlurb(Severity.LOW).isNotBlank())
    }
}
