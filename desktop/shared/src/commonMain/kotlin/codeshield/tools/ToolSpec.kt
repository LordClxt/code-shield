package codeshield.tools

/** OS supportés pour l'auto-installation des scanners. */
enum class OsKind { MACOS, WINDOWS, LINUX }

/** Architectures supportées. */
enum class Arch { ARM64, X64 }

/**
 * Spécifications d'installation gérée.
 *
 * - Gitleaks : binaire statique unique depuis les releases GitHub officielles
 *   (nomenclature goreleaser vérifiée : `gitleaks_<v>_<os>_<arch>.tar.gz|.zip`).
 * - Semgrep : pas de binaire standalone officiel -> installation via pip
 *   (`python3 -m pip install --target <dir> semgrep`).
 * - Dependency-Check : exclu (JVM + base NVD de plusieurs Go, installation manuelle).
 */
object ManagedTools {
    const val GITLEAKS_VERSION = "8.30.1"
    const val GITLEAKS_REPO = "gitleaks/gitleaks"
    const val SEMGREP_PIP_PACKAGE = "semgrep"
    const val SEMGREP_MIN_PYTHON = "python3"

    /**
     * Nom d'asset Gitleaks pour un couple OS/arch, ou null si non supporté
     * (ex. linux/arm64 n'est pas publié).
     */
    fun gitleaksAsset(os: OsKind, arch: Arch): String? {
        val suffix = when (os) {
            OsKind.MACOS -> when (arch) {
                Arch.ARM64 -> "darwin_arm64.tar.gz"
                Arch.X64 -> "darwin_x64.tar.gz"
            }
            OsKind.LINUX -> when (arch) {
                Arch.X64 -> "linux_x64.tar.gz"
                Arch.ARM64 -> null
            }
            OsKind.WINDOWS -> when (arch) {
                Arch.X64 -> "windows_x64.zip"
                Arch.ARM64 -> null
            }
        } ?: return null
        return "gitleaks_${GITLEAKS_VERSION}_$suffix"
    }

    fun gitleaksUrl(asset: String): String =
        "https://github.com/$GITLEAKS_REPO/releases/download/v$GITLEAKS_VERSION/$asset"

    fun gitleaksChecksumsUrl(): String =
        "https://github.com/$GITLEAKS_REPO/releases/download/v$GITLEAKS_VERSION/" +
            "gitleaks_${GITLEAKS_VERSION}_checksums.txt"

    /** Binaire attendu après extraction (les archives ne contiennent que lui). */
    fun gitleaksBinaryName(os: OsKind): String =
        if (os == OsKind.WINDOWS) "gitleaks.exe" else "gitleaks"

    /** Répertoire des scripts d'un `pip install --target` (semgrep). */
    fun pipScriptDir(targetDir: String, os: OsKind): String =
        if (os == OsKind.WINDOWS) "$targetDir/Scripts" else "$targetDir/bin"

    fun semgrepBinaryName(os: OsKind): String =
        if (os == OsKind.WINDOWS) "semgrep.exe" else "semgrep"

    /** Binaire OCaml compagnon (execvp depuis le wrapper Python). */
    fun semgrepCoreBinaryName(): String = "pysemgrep"

    // -- Trivy (archive goreleaser + checksums.txt) --------------------------
    // Versions épinglées (pas "latest") et vérifiées par SHA-256.
    const val TRIVY_VERSION = "0.74.0"
    const val TRIVY_REPO = "aquasecurity/trivy"

    fun trivyAsset(os: OsKind, arch: Arch): String {
        val suffix = when (os) {
            OsKind.MACOS -> if (arch == Arch.ARM64) "macOS-ARM64.tar.gz" else "macOS-64bit.tar.gz"
            OsKind.LINUX -> if (arch == Arch.ARM64) "Linux-ARM64.tar.gz" else "Linux-64bit.tar.gz"
            OsKind.WINDOWS -> "windows-64bit.zip"
        }
        return "trivy_${TRIVY_VERSION}_$suffix"
    }

    fun trivyUrl(asset: String): String =
        "https://github.com/$TRIVY_REPO/releases/download/v$TRIVY_VERSION/$asset"

    fun trivyChecksumsUrl(): String =
        "https://github.com/$TRIVY_REPO/releases/download/v$TRIVY_VERSION/trivy_${TRIVY_VERSION}_checksums.txt"

    fun trivyBinaryName(os: OsKind): String = if (os == OsKind.WINDOWS) "trivy.exe" else "trivy"

    // -- OSV-Scanner (binaire nu + SHA256SUMS) --------------------------------
    const val OSV_VERSION = "2.6.0"
    const val OSV_REPO = "google/osv-scanner"

    fun osvAsset(os: OsKind, arch: Arch): String {
        val a = if (arch == Arch.ARM64) "arm64" else "amd64"
        return when (os) {
            OsKind.MACOS -> "osv-scanner_darwin_$a"
            OsKind.LINUX -> "osv-scanner_linux_$a"
            OsKind.WINDOWS -> "osv-scanner_windows_$a.exe"
        }
    }

    fun osvUrl(asset: String): String =
        "https://github.com/$OSV_REPO/releases/download/v$OSV_VERSION/$asset"

    fun osvChecksumsUrl(): String =
        "https://github.com/$OSV_REPO/releases/download/v$OSV_VERSION/osv-scanner_SHA256SUMS"

    fun osvBinaryName(os: OsKind): String = if (os == OsKind.WINDOWS) "osv-scanner.exe" else "osv-scanner"

    // -- Outils Python installés via `pip --target` ---------------------------
    /** Paquet pip -> nom du script installé. */
    val PIP_TOOLS: Map<codeshield.model.ToolName, String> = mapOf(
        codeshield.model.ToolName.SEMGREP to SEMGREP_PIP_PACKAGE,
        codeshield.model.ToolName.BANDIT to "bandit",
        codeshield.model.ToolName.CHECKOV to "checkov",
    )

    fun pipScriptName(script: String, os: OsKind): String =
        if (os == OsKind.WINDOWS) "$script.exe" else script
}
