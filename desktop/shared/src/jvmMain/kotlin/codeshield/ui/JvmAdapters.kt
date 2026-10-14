package codeshield.ui

import codeshield.cache.InMemoryReportCache
import codeshield.scanner.ProcessRunner
import codeshield.sync.KtorSyncClient
import kotlinx.coroutines.flow.map
import java.io.File

/** Adapte ProcessRunner (JVM) au contrat CommandRunner du ViewModel commun. */
val JvmCommandRunner: CommandRunner = { command, workDir ->
    ProcessRunner.runStreaming(command, workDir).map { it.toTerminalLine(command) }
}

/** Session interactive (Claude Code) : consigne et réponses aux permissions sur l'entrée standard. */
val JvmInteractiveCommandRunner: InteractiveCommandRunner = { command, workDir, input ->
    ProcessRunner.runStreaming(command, workDir, input = input).map { it.toTerminalLine(command) }
}

private fun codeshield.scanner.ProcessEvent.toTerminalLine(command: List<String>): TerminalLine = when (this) {
    is codeshield.scanner.ProcessEvent.Stdout -> TerminalLine.Out(line)
    is codeshield.scanner.ProcessEvent.Stderr -> TerminalLine.Err(line)
    is codeshield.scanner.ProcessEvent.Exit -> TerminalLine.Exit(command.firstOrNull() ?: "unknown", code)
}

/** Taille max d'un fichier affiché dans le visualiseur (évite de charger un bundle minifié de 50 Mo). */
const val MAX_VIEWER_BYTES = 4L * 1024 * 1024

/**
 * Lecture fichier locale (visualiseur de code). Le contenu ne quitte jamais la machine.
 * Lève une exception explicite (affichée dans l'UI) si le fichier est absent ou trop gros.
 */
val JvmFileReader: FileReader = { path ->
    val f = File(path)
    require(f.isFile) { "Fichier introuvable : $path" }
    require(f.length() <= MAX_VIEWER_BYTES) { "Fichier trop volumineux pour l'aperçu (${f.length() / 1024} Ko)" }
    f.readLines()
}

/** Lecture des rapports JSON des moteurs (temporaires, hors dépôt) : pas de limite d'aperçu. */
val JvmReportReader: FileReader = { path ->
    File(path).takeIf { it.isFile }?.readLines() ?: emptyList()
}

private val SKIP_DIRS = setOf(".git", "node_modules", ".venv", "venv", "build", "dist", "target", ".gradle", ".idea", "__pycache__")

/** Profil du projet : extensions et noms de fichiers (parcours borné, sans lire le contenu). */
val JvmProjectProfiler: ProjectProfiler = { dir ->
    val exts = HashSet<String>()
    val names = HashSet<String>()
    var seen = 0
    File(dir).walkTopDown()
        .onEnter { it.name !in SKIP_DIRS }
        .maxDepth(8)
        .filter { it.isFile }
        .takeWhile { seen++ < 30_000 }
        .forEach { f ->
            names.add(f.name.lowercase())
            f.extension.lowercase().takeIf { it.isNotEmpty() }?.let(exts::add)
        }
    codeshield.scanner.ProjectProfile(extensions = exts, fileNames = names)
}

/** Dossier temporaire par scan : les rapports JSON ne polluent jamais le dépôt. */
val JvmScanWorkDirs = ScanWorkDirs(
    create = { java.nio.file.Files.createTempDirectory("codeshield-scan").toString() },
    delete = { runCatching { File(it).deleteRecursively() } },
)

/** Fabrique Ktor pour le Cloud Sync Trigger. */
val JvmSyncFactory: SyncClientFactory = { baseUrl, token ->
    KtorSyncClient(baseUrl, tokenProvider = { token }, http = sharedSyncHttp)
}

/** Un seul client HTTP pour toutes les synchros (un client par envoi n'était jamais fermé). */
private val sharedSyncHttp by lazy { KtorSyncClient.defaultClient() }

/** Cache local des rapports (v1 : mémoire ; v2 : SQLDelight). */
val LocalCache = InMemoryReportCache()

/**
 * Dossiers d'installation courants absents du PATH d'une app lancée depuis le Finder
 * (qui n'hérite pas du profil shell) : ex. `claude` dans `~/.local/bin`.
 */
internal fun userBinDirs(
    home: String = System.getProperty("user.home"),
    appData: String? = System.getenv("APPDATA"),
): List<String> = listOfNotNull(
    "$home/.local/bin", "$home/.claude/local", "$home/.opencode/bin", "$home/.bun/bin", "$home/.npm-global/bin",
    "$home/bin", "/opt/homebrew/bin", "/usr/local/bin",
    appData?.let { "$it/npm" },
)

private val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)

/** Commande shell d'une ligne (commande bloquée relancée par l'utilisateur). */
val JvmShell: (String) -> List<String> = { cmd -> if (isWindows) listOf("cmd", "/c", cmd) else listOf("sh", "-c", cmd) }

/** Noms de fichier possibles d'un binaire : `claude.exe` / `claude.cmd` sous Windows. */
internal fun binaryFileNames(binary: String, windows: Boolean = isWindows): List<String> =
    if (windows) listOf("$binary.exe", "$binary.cmd", binary) else listOf(binary)

/**
 * Localise un binaire via `which` (Unix) / `where` (Windows), puis dans [userBinDirs].
 * Retourne le chemin absolu ou null si introuvable (moteur ignoré, pas de crash).
 */
val JvmBinaryLocator: BinaryLocator = { binary ->
    whichBinary(binary) ?: userBinDirs()
        .flatMap { dir -> binaryFileNames(binary).map { File(dir, it) } }
        .firstOrNull { it.isFile && it.canExecute() }
        ?.absolutePath
}

private fun whichBinary(binary: String): String? =
    runCatching {
        val probe = if (isWindows) {
            listOf("where", binary)
        } else {
            listOf("sh", "-c", "command -v $binary")
        }
        val process = ProcessBuilder(probe).redirectErrorStream(true).start()
        process.outputStream.close()
        val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val code = process.waitFor()
        if (code == 0 && out.isNotBlank()) out.lineSequence().first().trim() else null
    }.getOrNull()

/** Vérifie l'existence d'un dossier projet (pré-vol du scan). */
val JvmDirChecker: DirChecker = { dir ->
    runCatching { File(dir).isDirectory }.getOrDefault(false)
}

/**
 * Dossier de démarrage du sélecteur : le dossier déjà saisi s'il existe,
 * sinon le dossier utilisateur. Pure -> testée.
 */
fun resolveStartDirectory(currentDir: String, homeDir: String = System.getProperty("user.home")): File {
    val candidate = currentDir.takeIf { it.isNotBlank() }?.let(::File)
    if (candidate != null && candidate.isDirectory) return candidate
    return File(homeDir)
}

/**
 * Sélecteur de dossier projet (JFileChooser en mode DIRECTORIES_ONLY).
 * Le FileDialog AWT précédent ne permettait de choisir que des fichiers.
 */
fun pickJvmDirectory(currentDir: String): String? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = "Dossier du projet à scanner"
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        isMultiSelectionEnabled = false
        currentDirectory = resolveStartDirectory(currentDir)
    }
    val approved = if (javax.swing.SwingUtilities.isEventDispatchThread()) {
        chooser.showOpenDialog(null)
    } else {
        var result = javax.swing.JFileChooser.CANCEL_OPTION
        javax.swing.SwingUtilities.invokeAndWait {
            result = chooser.showOpenDialog(null)
        }
        result
    }
    if (approved != javax.swing.JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.takeIf { it.isDirectory }?.absolutePath
}
