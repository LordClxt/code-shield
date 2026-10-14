package codeshield.scanner

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessRunnerTest {

    @Test
    fun streamsStdoutThenExitCode() = runTest {
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) return@runTest
        val events = ProcessRunner.runStreaming(listOf("echo", "hello-codeshield"), null).toList()
        val out = events.filterIsInstance<ProcessEvent.Stdout>().map { it.line }
        val exit = events.filterIsInstance<ProcessEvent.Exit>().single()
        assertTrue(out.any { it.contains("hello-codeshield") }, out.toString())
        assertEquals(0, exit.code)
    }

    @Test
    fun nonZeroExitIsReported() = runTest {
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) return@runTest
        val events = ProcessRunner.runStreaming(listOf("sh", "-c", "exit 3"), null).toList()
        assertEquals(3, events.filterIsInstance<ProcessEvent.Exit>().single().code)
    }

    @Test
    fun stderrIsCapturedSeparately() = runTest {
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) return@runTest
        val events = ProcessRunner.runStreaming(
            listOf("sh", "-c", "echo oops >&2"), null,
        ).toList()
        val err = events.filterIsInstance<ProcessEvent.Stderr>().map { it.line }
        assertTrue(err.any { it.contains("oops") }, err.toString())
    }

    @Test
    fun stdinIsWrittenThenClosed() = runTest {
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) return@runTest
        val prompt = "ligne 1 & \"guillemets\" | `x`\nligne 2"
        val events = ProcessRunner.runStreaming(listOf("cat"), null, stdin = prompt).toList()
        assertEquals(prompt.lines(), events.filterIsInstance<ProcessEvent.Stdout>().map { it.line })
        assertEquals(0, events.filterIsInstance<ProcessEvent.Exit>().single().code)
    }
}
