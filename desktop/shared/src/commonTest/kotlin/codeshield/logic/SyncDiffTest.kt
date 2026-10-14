package codeshield.logic

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun finding(
    rule: String = "R1",
    path: String = "src/a.py",
    line: Int = 10,
): Finding = Finding(
    tool = ToolName.SEMGREP,
    ruleId = rule,
    filePath = path,
    lineNumber = line,
    severity = Severity.HIGH,
    title = "$rule title",
)

class SyncDiffTest {

    @Test
    fun emptyKnownMeansEverythingToCreate() {
        val result = SyncDiff.computeDiff(emptySet(), listOf(finding("R1"), finding("R2")))
        assertEquals(2, result.toCreate.size)
        assertTrue(result.toTouch.isEmpty())
        assertTrue(result.toResolve.isEmpty())
    }

    @Test
    fun sameScanMeansTouchOnly() {
        val findings = listOf(finding("R1"), finding("R2"))
        val known = findings.map { it.identityKey() }.toSet()
        val result = SyncDiff.computeDiff(known, findings)
        assertTrue(result.toCreate.isEmpty())
        assertEquals(2, result.toTouch.size)
        assertTrue(result.toResolve.isEmpty())
    }

    @Test
    fun missingFindingIsResolvedAndNewIsCreated() {
        val known = setOf(finding("R1").identityKey(), finding("R2").identityKey())
        val result = SyncDiff.computeDiff(known, listOf(finding("R2"), finding("R3")))
        assertEquals(listOf("R3"), result.toCreate.map { it.ruleId })
        assertEquals(listOf("R2"), result.toTouch.map { it.ruleId })
        assertEquals(listOf(finding("R1").identityKey()), result.toResolve)
    }

    @Test
    fun identityKeyIsLineSensitive() {
        val known = setOf(finding("R1", line = 10).identityKey())
        val result = SyncDiff.computeDiff(known, listOf(finding("R1", line = 20)))
        assertEquals(1, result.toCreate.size)
        assertEquals(1, result.toResolve.size)
    }

    @Test
    fun emptyResubmitResolvesEverything() {
        val known = setOf(finding("R1").identityKey())
        val result = SyncDiff.computeDiff(known, emptyList())
        assertTrue(result.toCreate.isEmpty())
        assertEquals(1, result.toResolve.size)
    }
}
