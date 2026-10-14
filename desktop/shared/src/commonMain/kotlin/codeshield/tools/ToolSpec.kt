package codeshield.tools

/** OS supportés pour l'auto-installation des scanners. */
enum class OsKind { MACOS, WINDOWS, LINUX }

/** Architectures supportées. */
enum class Arch { ARM64, X64 }

/**
 * Spécifications d'installation gérée. Tous les moteurs installés par l'app sont
 * des binaires autonomes publiés en release : aucun prérequis (ni Python, ni pip)
 * sur la machine.
 *
 * - Gitleaks : binaire statique unique depuis les releases GitHub officielles
 *   (nomenclature goreleaser vérifiée : `gitleaks_<v>_<os>_<arch>.tar.gz|.zip`).
 * - Opengrep : moteur SAST (fork LGPL de Semgrep, mêmes règles, même JSON),
 *   binaire autonome par OS.
 * - Dependency-Check : exclu (JVM + base NVD de plusieurs Go, installation manuelle).
 */
object ManagedTools {
    const val GITLEAKS_VERSION = "8.30.1"
    const val GITLEAKS_REPO = "gitleaks/gitleaks"

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

    // -- Opengrep (binaire nu, SHA-256 épinglé) -------------------------------
    // La release ne publie pas de fichier de sommes (seulement des signatures
    // cosign) : les empreintes sont celles exposées par GitHub pour chaque asset
    // de la version épinglée, vérifiées au téléchargement.
    const val OPENGREP_VERSION = "1.30.1"
    const val OPENGREP_REPO = "opengrep/opengrep"

    /** Linux : build glibc (manylinux). Windows : x86_64 uniquement (émulé sur ARM). */
    fun opengrepAsset(os: OsKind, arch: Arch): String = when (os) {
        OsKind.MACOS -> if (arch == Arch.ARM64) "opengrep_osx_arm64" else "opengrep_osx_x86"
        OsKind.LINUX -> if (arch == Arch.ARM64) "opengrep_manylinux_aarch64" else "opengrep_manylinux_x86"
        OsKind.WINDOWS -> "opengrep_windows_x86.exe"
    }

    val OPENGREP_SHA256: Map<String, String> = mapOf(
        "opengrep_osx_arm64" to "7b788794e111ce3fb83f0aa52c100a3b85d37d8b59482262109031f50d4a8d91",
        "opengrep_osx_x86" to "a8c6d5f51bb38253b48e9a80fca63684aa2a0f9abfeacea27b8d8b5778476bae",
        "opengrep_manylinux_aarch64" to "a730f6fdce1e978ea29e610a2e0503bfe1ec31fce08979a9a84e3699f03e16ee",
        "opengrep_manylinux_x86" to "d3195b9d8d5ae93179f6aa5f5daaba6a920a5a09d38c5d5ae5e60924050210c4",
        "opengrep_windows_x86.exe" to "d3a45326ff63cabe90d94ebc679fcd4302440178a0e5bf0248a6f7b610db3b53",
    )

    fun opengrepUrl(asset: String): String =
        "https://github.com/$OPENGREP_REPO/releases/download/v$OPENGREP_VERSION/$asset"

    fun opengrepBinaryName(os: OsKind): String = if (os == OsKind.WINDOWS) "opengrep.exe" else "opengrep"

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
}
