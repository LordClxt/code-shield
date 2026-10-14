package codeshield.cache

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ScanHistoryTest {
    private fun scan(dir: String, at: Long) = PersistedScan(
        projectDir = dir,
        findings = listOf(Finding(ToolName.SEMGREP, "R1", "a.py", 1, Severity.HIGH, "t")),
        terminal = listOf("$ scan", "Terminé : 1 findings"),
        finishedAtEpochMs = at,
    )

    @Test
    fun saveAndLoadLatest() = runTest {
        val store = InMemoryHistoryStore()
        assertNull(store.load("/proj"))
        store.save(scan("/proj", 1000))
        store.save(scan("/proj", 2000))
        assertEquals(2000, store.load("/proj")?.finishedAtEpochMs)
        assertEquals(2, store.list().size)
    }

    @Test
    fun loadMetaRoundTrip() = runTest {
        val store = InMemoryHistoryStore()
        store.save(scan("/proj", 1234))
        val meta = store.list().first()
        assertEquals(1234, meta.finishedAtEpochMs)
        assertEquals(1, meta.findingsCount)
        assertTrue(store.loadMeta("/proj", 1234)?.findings?.size == 1)
    }

    @Test
    fun clearRemovesProject() = runTest {
        val store = InMemoryHistoryStore()
        store.save(scan("/a", 1))
        store.save(scan("/b", 2))
        store.clear("/a")
        assertNull(store.load("/a"))
        assertEquals(1, store.list().size)
    }
}
