package codeshield.tools

import codeshield.model.ToolName
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest

/** SHA-256 hexadécimal (vérification des archives téléchargées). */
fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

/** Extrait une archive .zip dans `dest` (sécurisé : refuse les chemins absolus / `..`). */
fun extractZip(zipBytes: ByteArray, dest: File) {
    dest.mkdirs()
    java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val target = dest.resolve(entry.name).normalize()
            require(target.absolutePath.startsWith(dest.absolutePath)) { "zip entry hors cible : ${entry.name}" }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { out -> zip.copyTo(out) }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
}

/** Extrait une archive .tar.gz dans `dest` (sécurisé : refuse les chemins absolus / `..`). */
fun extractTarGz(tgzBytes: ByteArray, dest: File) {
    dest.mkdirs()
    val gzip = java.util.zip.GZIPInputStream(tgzBytes.inputStream())
    val tar = org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzip)
    tar.use { archive ->
        var entry = archive.nextEntry
        while (entry != null) {
            val target = dest.resolve(entry.name).normalize()
            require(target.absolutePath.startsWith(dest.absolutePath)) { "tar entry hors cible : ${entry.name}" }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { out -> archive.copyTo(out) }
            }
            entry = archive.nextEntry
        }
    }
}

/**
 * Installation gérée des scanners dans `~/.codeshield/bin` :
 * - Gitleaks : asset GitHub officiel, vérifié par SHA-256 (checksums.txt de la release).
 * - Semgrep : `pip install --target` (pas de binaire standalone officiel).
 * - Dependency-Check : non supporté (manuel uniquement).
 */
class JvmToolInstaller(
    private val toolsDir: File = File(System.getProperty("user.home"), ".codeshield/bin"),
    private val locator: (String) -> String? = { binary ->
        runCatching {
            val probe = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                listOf("where", binary)
            } else {
                listOf("sh", "-c", "command -v $binary")
            }
            val process = ProcessBuilder(probe).redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && out.isNotBlank()) out.lineSequence().first().trim() else null
        }.getOrNull()
    },
) : ToolInstaller {

    internal fun osKind(): OsKind {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> OsKind.MACOS
            os.contains("win") -> OsKind.WINDOWS
            else -> OsKind.LINUX
        }
    }

    internal fun arch(): Arch {
        val arch = System.getProperty("os.arch").lowercase()
        return if (arch.contains("aarch64") || arch.contains("arm64")) Arch.ARM64 else Arch.X64
    }

    private fun gitleaksFile(): File =
        File(toolsDir, ManagedTools.gitleaksBinaryName(osKind()))

    private fun semgrepLibsDir(): File = File(toolsDir, "semgrep-pylibs")

    private fun semgrepFile(): File =
        File(ManagedTools.pipScriptDir(semgrepLibsDir().absolutePath, osKind()), ManagedTools.semgrepBinaryName(osKind()))

    /** `<tool>-pylibs` : un `pip --target` isolé par outil Python (bandit, checkov). */
    private fun pipLibsDir(tool: ToolName): File =
        if (tool == ToolName.SEMGREP) semgrepLibsDir() else File(toolsDir, "${tool.name.lowercase()}-pylibs")

    private fun pipScriptFile(tool: ToolName): File = if (tool == ToolName.SEMGREP) semgrepFile() else File(
        ManagedTools.pipScriptDir(pipLibsDir(tool).absolutePath, osKind()),
        ManagedTools.pipScriptName(ManagedTools.PIP_TOOLS.getValue(tool), osKind()),
    )

    private fun trivyFile(): File = File(toolsDir, ManagedTools.trivyBinaryName(osKind()))

    private fun osvFile(): File = File(toolsDir, ManagedTools.osvBinaryName(osKind()))

    override fun installedPath(tool: ToolName): String? = when (tool) {
        ToolName.GITLEAKS -> gitleaksFile()
        ToolName.SEMGREP, ToolName.BANDIT, ToolName.CHECKOV -> pipScriptFile(tool)
        ToolName.TRIVY -> trivyFile()
        ToolName.OSV_SCANNER -> osvFile()
        ToolName.DEPENDENCY_CHECK -> null
    }?.takeIf { it.isFile }?.absolutePath

    override suspend fun status(tool: ToolName): ToolStatus {
        val path = installedPath(tool)
        return if (path != null) ToolStatus(tool, ToolState.READY, path = path)
        else ToolStatus(tool, ToolState.MISSING)
    }

    override suspend fun install(tool: ToolName, onProgress: (Float) -> Unit): Result<String> =
        runCatching {
            when (tool) {
                ToolName.GITLEAKS -> installGitleaks(onProgress)
                ToolName.SEMGREP -> installSemgrepViaPip(onProgress)
                ToolName.BANDIT, ToolName.CHECKOV -> installViaPip(tool, onProgress)
                ToolName.TRIVY -> installTrivy(onProgress)
                ToolName.OSV_SCANNER -> installOsv(onProgress)
                ToolName.DEPENDENCY_CHECK -> throw UnsupportedOperationException(
                    "Dependency-Check n'est pas auto-installable (JVM + base NVD) : " +
                        PrerequisitesHint.dependencyCheckManual,
                )
            }
        }

    // -- Gitleaks -----------------------------------------------------------

    private fun installGitleaks(onProgress: (Float) -> Unit): String {
        val os = osKind()
        val asset = ManagedTools.gitleaksAsset(os, arch())
            ?: throw UnsupportedOperationException("Gitleaks ne publie pas de binaire pour $os/${arch()}")
        onProgress(0.05f)
        val bytes = download(ManagedTools.gitleaksUrl(asset)) { onProgress(0.05f + 0.7f * it) }
        onProgress(0.8f)
        verifyChecksum(asset, bytes)
        val stage = Files.createTempDirectory("codeshield-gitleaks").toFile()
        try {
            if (asset.endsWith(".zip")) extractZip(bytes, stage) else extractTarGz(bytes, stage)
            val binary = stage.resolve(ManagedTools.gitleaksBinaryName(os))
                .takeIf { it.isFile }
                ?: throw IllegalStateException("archive $asset sans binaire ${ManagedTools.gitleaksBinaryName(os)}")
            toolsDir.mkdirs()
            val target = gitleaksFile()
            binary.copyTo(target, overwrite = true)
            makeExecutable(target)
            clearQuarantine(target)
        } finally {
            stage.deleteRecursively()
        }
        onProgress(1f)
        return gitleaksFile().absolutePath
    }

    // -- Trivy / OSV-Scanner (releases GitHub, SHA-256 vérifié) ----------------

    private fun installTrivy(onProgress: (Float) -> Unit): String {
        val os = osKind()
        val asset = ManagedTools.trivyAsset(os, arch())
        onProgress(0.05f)
        val bytes = download(ManagedTools.trivyUrl(asset)) { onProgress(0.05f + 0.7f * it) }
        onProgress(0.8f)
        verifyChecksum(asset, bytes, ManagedTools.trivyChecksumsUrl())
        val stage = Files.createTempDirectory("codeshield-trivy").toFile()
        try {
            if (asset.endsWith(".zip")) extractZip(bytes, stage) else extractTarGz(bytes, stage)
            val binary = stage.resolve(ManagedTools.trivyBinaryName(os)).takeIf { it.isFile }
                ?: throw IllegalStateException("archive $asset sans binaire ${ManagedTools.trivyBinaryName(os)}")
            installBinary(binary, trivyFile())
        } finally {
            stage.deleteRecursively()
        }
        onProgress(1f)
        return trivyFile().absolutePath
    }

    private fun installOsv(onProgress: (Float) -> Unit): String {
        val asset = ManagedTools.osvAsset(osKind(), arch())
        onProgress(0.05f)
        val bytes = download(ManagedTools.osvUrl(asset)) { onProgress(0.05f + 0.8f * it) }
        verifyChecksum(asset, bytes, ManagedTools.osvChecksumsUrl())
        val stage = Files.createTempFile("codeshield-osv", "").toFile()
        try {
            stage.writeBytes(bytes)
            installBinary(stage, osvFile())
        } finally {
            stage.delete()
        }
        onProgress(1f)
        return osvFile().absolutePath
    }

    private fun installBinary(source: File, target: File) {
        toolsDir.mkdirs()
        source.copyTo(target, overwrite = true)
        makeExecutable(target)
        clearQuarantine(target)
    }

    // -- Bandit / Checkov (pip --target isolé) ---------------------------------

    private fun installViaPip(tool: ToolName, onProgress: (Float) -> Unit): String {
        val pkg = ManagedTools.PIP_TOOLS.getValue(tool)
        val python = locator(ManagedTools.SEMGREP_MIN_PYTHON)
            ?: throw IllegalStateException("python3 introuvable — installez Python 3.9+ (ou `pipx install $pkg`)")
        onProgress(-1f)
        val targetDir = pipLibsDir(tool)
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        val process = ProcessBuilder(
            python, "-m", "pip", "install", "--quiet", "--upgrade", "--target", targetDir.absolutePath, pkg,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) throw IllegalStateException("pip install $pkg a échoué (code $code) : ${output.takeLast(2000)}")
        val script = pipScriptFile(tool)
        if (!script.isFile) throw IllegalStateException("$pkg installé mais script introuvable : $script")
        makeExecutable(script)
        onProgress(1f)
        return script.absolutePath
    }

    private fun verifyChecksum(asset: String, bytes: ByteArray, checksumsUrl: String = ManagedTools.gitleaksChecksumsUrl()) {
        val checksums = downloadText(checksumsUrl)
        // Format : "<sha256>  <asset>" par ligne.
        val expected = checksums.lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 2 && it[1].trimStart('*') == asset }
            ?.get(0)
            ?: throw IllegalStateException("checksum introuvable pour $asset — abandon (sécurité)")
        val actual = sha256Hex(bytes)
        require(actual.equals(expected, ignoreCase = true)) {
            "checksum invalide pour $asset (corrompu ou compromis) — abandon"
        }
    }

    // -- Semgrep (pip) ------------------------------------------------------

    private fun installSemgrepViaPip(onProgress: (Float) -> Unit): String {
        val python = locator(ManagedTools.SEMGREP_MIN_PYTHON)
            ?: throw IllegalStateException(
                "python3 introuvable — installez Python 3.9+ puis relancez " +
                    "(ou `brew install semgrep` / `pip install semgrep`)",
            )
        onProgress(-1f)
        val targetDir = semgrepLibsDir()
        // Rend l'installation idempotente : `pip install --target` échoue avec
        // FileExistsError (ex. `wcmatch-*.dist-info` déjà présent) si on relance
        // Download après un install partiel/ancien. On purge donc la cible avant.
        // (Visible aussi avec Python 3.14 où shutil._copytree est plus strict.)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
        val target = targetDir.absolutePath
        val process = ProcessBuilder(
            python, "-m", "pip", "install", "--quiet", "--upgrade", "--target", target, ManagedTools.SEMGREP_PIP_PACKAGE,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) {
            throw IllegalStateException("pip install semgrep a échoué (code $code) : ${output.takeLast(2000)}")
        }
        val script = semgrepFile()
        if (!script.isFile) throw IllegalStateException("semgrep installé mais script introuvable : $script")
        makeExecutable(script)
        // Le wrapper OCaml `pysemgrep` (execvp) doit aussi être exécutable.
        script.parentFile?.resolve(ManagedTools.semgrepCoreBinaryName())?.takeIf { it.isFile }?.let(::makeExecutable)
        onProgress(1f)
        return script.absolutePath
    }

    // -- Primitives ---------------------------------------------------------

    internal fun download(url: String, onProgress: (Float) -> Unit = {}): ByteArray {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "CodeShield-Desktop/1.0")
        connection.setRequestProperty("Accept", "application/octet-stream")
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        try {
            val code = connection.responseCode
            require(code in 200..299) { "téléchargement $url : HTTP $code" }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    read += n
                    if (total != null) onProgress((read.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadText(url: String): String = download(url).toString(Charsets.UTF_8)

    private fun makeExecutable(file: File) {
        runCatching {
            val perms = Files.getPosixFilePermissions(file.toPath()).toMutableSet()
            perms.add(PosixFilePermission.OWNER_EXECUTE)
            Files.setPosixFilePermissions(file.toPath(), perms)
        }
    }

    /** Retire la quarantaine macOS (Gatekeeper) au mieux — échec silencieux sinon. */
    private fun clearQuarantine(file: File) {
        if (osKind() != OsKind.MACOS) return
        runCatching {
            ProcessBuilder("xattr", "-d", "com.apple.quarantine", file.absolutePath)
                .redirectErrorStream(true).start().waitFor()
        }
    }
}

/** Hints manuels (outils non auto-installables). */
object PrerequisitesHint {
    const val dependencyCheckManual: String =
        "`brew install dependency-check` (ou https://owasp.org/www-project-dependency-check/)"
}
