package codeshield.cache

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryReportCacheTest {

    private fun report(dir: String) = CachedReport(
        projectDir = dir,
        findings = listOf(
            Finding(ToolName.SEMGREP, "R1", "a.py", 1, Severity.HIGH, "t"),
        ),
    )

    @Test
    fun saveAndLoadRoundTrip() = runTest {
        val cache = InMemoryReportCache()
        assertNull(cache.load("/proj"))
        cache.save(report("/proj"))
        assertEquals(1, cache.load("/proj")?.findings?.size)
    }

    @Test
    fun clearRemovesEntry() = runTest {
        val cache = InMemoryReportCache()
        cache.save(report("/proj"))
        cache.clear("/proj")
        assertNull(cache.load("/proj"))
    }

    @Test
    fun entriesAreIsolatedPerProject() = runTest {
        val cache = InMemoryReportCache()
        cache.save(report("/a"))
        assertNull(cache.load("/b"))
    }
}
