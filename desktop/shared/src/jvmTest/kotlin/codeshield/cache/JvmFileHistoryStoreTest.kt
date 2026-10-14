package codeshield.cache

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class JvmFileHistoryStoreTest {
    @Test
    fun fileRoundTripSurvives() = runTest {
        val dir = Files.createTempDirectory("codeshield-history").toFile()
        try {
            val store = JvmFileHistoryStore(scansDir = dir)
            val scan = PersistedScan(
                projectDir = "/tmp/proj",
                findings = listOf(Finding(ToolName.GITLEAKS, "R", "f", 2, Severity.HIGH, "t")),
                terminal = listOf("line1", "line2"),
                finishedAtEpochMs = 9999,
            )
            store.save(scan)
            assertEquals(1, store.list().size)
            val loaded = store.load("/tmp/proj")
            assertEquals(1, loaded?.findings?.size)
            assertTrue(loaded?.terminal?.size == 2)
        } finally {
            dir.deleteRecursively()
        }
    }
}
