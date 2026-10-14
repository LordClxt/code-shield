package codeshield.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Événements temps réel du pipeline (Real-time Pipeline Terminal). */
sealed interface ProcessEvent {
    data class Stdout(val line: String) : ProcessEvent
    data class Stderr(val line: String) : ProcessEvent
    data class Exit(val code: Int) : ProcessEvent
}

/**
 * Exécute un CLI via ProcessBuilder et diffuse STDOUT/STDERR **au fil de l'eau**
 * (deux lecteurs concurrents : un stderr bavard ne bloque plus stdout).
 *
 * Annulation : si le collecteur est annulé (bouton Stop, timeout), le processus
 * et ses descendants sont tués.
 */
object ProcessRunner {
    fun runStreaming(
        command: List<String>,
        workDir: String? = null,
        extraEnv: Map<String, String> = emptyMap(),
        /** Texte écrit sur l'entrée standard (puis fermée) : évite le quoting des arguments sous Windows. */
        stdin: String? = null,
    ): Flow<ProcessEvent> = channelFlow {
        require(command.isNotEmpty()) { "command must not be empty" }
        val builder = ProcessBuilder(command).apply {
            if (workDir != null) directory(java.io.File(workDir))
            if (extraEnv.isNotEmpty()) environment().putAll(extraEnv)
            redirectInput(ProcessBuilder.Redirect.PIPE)
        }
        val process = withContext(Dispatchers.IO) { builder.start() }
        try {
            if (stdin == null) {
                process.outputStream.close() // aucun moteur n'attend d'entrée
            } else {
                // Un processus qui sort sans tout lire casse le tube : son code de sortie fait foi.
                launch(Dispatchers.IO) { runCatching { process.outputStream.bufferedWriter().use { it.write(stdin) } } }
            }
            val out = launch(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach { send(ProcessEvent.Stdout(it)) } }
            }
            val err = launch(Dispatchers.IO) {
                process.errorStream.bufferedReader().useLines { lines -> lines.forEach { send(ProcessEvent.Stderr(it)) } }
            }
            val code = runInterruptible(Dispatchers.IO) { process.waitFor() }
            out.join()
            err.join()
            send(ProcessEvent.Exit(code))
        } finally {
            if (process.isAlive) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }.flowOn(Dispatchers.IO)
}
