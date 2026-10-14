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
        assertEquals("semgrep", ManagedTools.semgrepBinaryName(OsKind.LINUX))
        assertEquals("semgrep.exe", ManagedTools.semgrepBinaryName(OsKind.WINDOWS))
    }

    @Test
    fun pipLayoutFollowsPipTargetConvention() {
        assertEquals("/t/bin", ManagedTools.pipScriptDir("/t", OsKind.MACOS))
        assertEquals("/t/bin", ManagedTools.pipScriptDir("/t", OsKind.LINUX))
        assertEquals("C:/t/Scripts", ManagedTools.pipScriptDir("C:/t", OsKind.WINDOWS))
    }
}
