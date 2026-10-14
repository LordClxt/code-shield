package codeshield.ui

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun finding(
    path: String,
    tool: ToolName = ToolName.SEMGREP,
    rule: String = "R1",
    line: Int = 10,
    severity: Severity = Severity.HIGH,
): Finding = Finding(tool, rule, path, line, severity, "$rule title")

class FindingGroupsTest {

    @Test
    fun parentDirExtractsFolder() {
        assertEquals("src", parentDir("src/app.py"))
        assertEquals("src/nested", parentDir("src/nested/a.py"))
        assertEquals("(root)", parentDir("app.py"))
        assertEquals("src", parentDir("src\\win.py"))
    }

    @Test
    fun groupsByFolderWithCounts() {
        val findings = listOf(
            finding("src/a.py"), finding("src/b.py"), finding("lib/c.py"),
        )
        val groups = groupFindings(findings, GroupTab.FOLDERS)
        assertEquals(2, groups.size)
        // Tri : count décroissant d'abord.
        assertEquals("src", groups[0].title)
        assertEquals(2, groups[0].count)
        assertEquals(2, groups[0].fileCount) // 2 fichiers distincts
        assertEquals(2, groups[0].findings.distinctBy { it.filePath }.size)
        assertEquals("lib", groups[1].title)
    }

    @Test
    fun groupsByToolWithLabels() {
        val findings = listOf(
            finding("a.py", ToolName.SEMGREP),
            finding("b.py", ToolName.GITLEAKS),
            finding("c.py", ToolName.GITLEAKS),
        )
        val groups = groupFindings(findings, GroupTab.TOOLS)
        assertEquals(listOf("Gitleaks", "Semgrep"), groups.map { it.title })
        assertEquals(listOf(ToolName.GITLEAKS), groups[0].tools)
    }

    @Test
    fun groupKeysAreUniquePerTab() {
        val findings = listOf(finding("src/a.py"))
        val folderKeys = groupFindings(findings, GroupTab.FOLDERS).map { it.key }
        val toolKeys = groupFindings(findings, GroupTab.TOOLS).map { it.key }
        assertTrue(folderKeys.none { it in toolKeys })
    }

    @Test
    fun filterMatchesFileRuleAndTitle() {
        val findings = listOf(
            finding("src/app.py", rule = "dangerous-exec"),
            finding("src/web.py", rule = "sqli"),
        )
        assertEquals(1, filterFindings(findings, "app.py").size)
        assertEquals(1, filterFindings(findings, "DANGEROUS").size) // insensible à la casse
        assertEquals(2, filterFindings(findings, "  ").size) // requête vide = tout
        assertTrue(filterFindings(findings, "zzz").isEmpty())
    }

    @Test
    fun countBySeverityCoversAllLevels() {
        val findings = listOf(
            finding("a.py", severity = Severity.CRITICAL),
            finding("b.py", severity = Severity.HIGH),
            finding("c.py", severity = Severity.HIGH),
        )
        val counts = countBySeverity(findings)
        assertEquals(1, counts[Severity.CRITICAL])
        assertEquals(2, counts[Severity.HIGH])
        assertEquals(0, counts[Severity.MEDIUM])
        assertEquals(0, counts[Severity.LOW])
    }

    @Test
    fun emptyFindingsProduceEmptyGroups() {
        assertTrue(groupFindings(emptyList(), GroupTab.FOLDERS).isEmpty())
        assertTrue(groupFindings(emptyList(), GroupTab.TOOLS).isEmpty())
    }
}
