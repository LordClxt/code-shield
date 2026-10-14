package codeshield.tools

import codeshield.model.ToolName
import codeshield.scanner.ScannerEngines
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
 * Installation gérée des scanners dans `~/.codeshield/bin`, uniquement des
 * binaires autonomes vérifiés par SHA-256 (aucun prérequis sur la machine) :
 * - Gitleaks, Trivy, OSV-Scanner : fichier de sommes publié avec la release.
 * - Opengrep : empreintes épinglées dans [ManagedTools.OPENGREP_SHA256].
 * - Bandit, Checkov, Dependency-Check : non installés (utilisés depuis le PATH).
 */
class JvmToolInstaller(
    private val toolsDir: File = File(System.getProperty("user.home"), ".codeshield/bin"),
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

    private fun opengrepFile(): File = File(toolsDir, ManagedTools.opengrepBinaryName(osKind()))

    private fun trivyFile(): File = File(toolsDir, ManagedTools.trivyBinaryName(osKind()))

    private fun osvFile(): File = File(toolsDir, ManagedTools.osvBinaryName(osKind()))

    override fun installedPath(tool: ToolName): String? = when (tool) {
        ToolName.GITLEAKS -> gitleaksFile()
        ToolName.SEMGREP -> opengrepFile()
        ToolName.TRIVY -> trivyFile()
        ToolName.OSV_SCANNER -> osvFile()
        ToolName.BANDIT, ToolName.CHECKOV, ToolName.DEPENDENCY_CHECK -> null
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
                ToolName.SEMGREP -> installOpengrep(onProgress)
                ToolName.BANDIT, ToolName.CHECKOV -> throw UnsupportedOperationException(
                    "${ScannerEngines.of(tool).label} n'est pas installé par l'app (paquet Python) : " +
                        ScannerEngines.of(tool).installHint,
                )
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

    private fun installOpengrep(onProgress: (Float) -> Unit): String {
        val asset = ManagedTools.opengrepAsset(osKind(), arch())
        val expected = ManagedTools.OPENGREP_SHA256[asset]
            ?: throw IllegalStateException("empreinte inconnue pour $asset — abandon (sécurité)")
        onProgress(0.05f)
        val bytes = download(ManagedTools.opengrepUrl(asset)) { onProgress(0.05f + 0.85f * it) }
        require(sha256Hex(bytes).equals(expected, ignoreCase = true)) {
            "checksum invalide pour $asset (corrompu ou compromis) — abandon"
        }
        val stage = Files.createTempFile("codeshield-opengrep", "").toFile()
        try {
            stage.writeBytes(bytes)
            installBinary(stage, opengrepFile())
        } finally {
            stage.delete()
        }
        onProgress(1f)
        return opengrepFile().absolutePath
    }

    private fun installBinary(source: File, target: File) {
        toolsDir.mkdirs()
        source.copyTo(target, overwrite = true)
        makeExecutable(target)
        clearQuarantine(target)
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
