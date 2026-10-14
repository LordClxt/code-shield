package codeshield.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedToolsTest {

    @Test
    fun gitleaksAssetsMatchPublishedReleases() {
        // Nomenclature goreleaser vérifiée contre les assets v8.30.1 (HTTP 302).
        assertEquals("gitleaks_8.30.1_darwin_arm64.tar.gz", ManagedTools.gitleaksAsset(OsKind.MACOS, Arch.ARM64))
        assertEquals("gitleaks_8.30.1_darwin_x64.tar.gz", ManagedTools.gitleaksAsset(OsKind.MACOS, Arch.X64))
        assertEquals("gitleaks_8.30.1_linux_x64.tar.gz", ManagedTools.gitleaksAsset(OsKind.LINUX, Arch.X64))
        assertEquals("gitleaks_8.30.1_windows_x64.zip", ManagedTools.gitleaksAsset(OsKind.WINDOWS, Arch.X64))
    }

    @Test
    fun unsupportedPlatformsReturnNull() {
        assertNull(ManagedTools.gitleaksAsset(OsKind.LINUX, Arch.ARM64))
        assertNull(ManagedTools.gitleaksAsset(OsKind.WINDOWS, Arch.ARM64))
    }

    @Test
    fun urlsPointToOfficialReleases() {
        val asset = ManagedTools.gitleaksAsset(OsKind.MACOS, Arch.ARM64)!!
        val url = ManagedTools.gitleaksUrl(asset)
        assertTrue(url.startsWith("https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/"), url)
        assertTrue(url.endsWith(asset), url)
        assertTrue(ManagedTools.gitleaksChecksumsUrl().endsWith("_checksums.txt"))
    }

    @Test
    fun binaryNamesArePlatformCorrect() {
        assertEquals("gitleaks", ManagedTools.gitleaksBinaryName(OsKind.MACOS))
        assertEquals("gitleaks", ManagedTools.gitleaksBinaryName(OsKind.LINUX))
        assertEquals("gitleaks.exe", ManagedTools.gitleaksBinaryName(OsKind.WINDOWS))
        assertEquals("opengrep", ManagedTools.opengrepBinaryName(OsKind.LINUX))
        assertEquals("opengrep.exe", ManagedTools.opengrepBinaryName(OsKind.WINDOWS))
    }

    @Test
    fun opengrepAssetsMatchPublishedReleaseAndArePinned() {
        // Noms vérifiés contre les assets de la release v1.30.1.
        val assets = listOf(
            ManagedTools.opengrepAsset(OsKind.MACOS, Arch.ARM64) to "opengrep_osx_arm64",
            ManagedTools.opengrepAsset(OsKind.MACOS, Arch.X64) to "opengrep_osx_x86",
            ManagedTools.opengrepAsset(OsKind.LINUX, Arch.ARM64) to "opengrep_manylinux_aarch64",
            ManagedTools.opengrepAsset(OsKind.LINUX, Arch.X64) to "opengrep_manylinux_x86",
            ManagedTools.opengrepAsset(OsKind.WINDOWS, Arch.X64) to "opengrep_windows_x86.exe",
            ManagedTools.opengrepAsset(OsKind.WINDOWS, Arch.ARM64) to "opengrep_windows_x86.exe",
        )
        for ((actual, expected) in assets) {
            assertEquals(expected, actual)
            // Chaque asset installable a une empreinte SHA-256 épinglée (64 hex).
            assertTrue(ManagedTools.OPENGREP_SHA256[actual]?.matches(Regex("[0-9a-f]{64}")) == true, actual)
        }
        val url = ManagedTools.opengrepUrl("opengrep_osx_arm64")
        assertEquals("https://github.com/opengrep/opengrep/releases/download/v1.30.1/opengrep_osx_arm64", url)
    }
}
