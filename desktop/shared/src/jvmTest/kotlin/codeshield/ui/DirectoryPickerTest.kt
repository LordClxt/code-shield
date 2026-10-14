package codeshield.ui

import java.nio.file.Files
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectoryPickerTest {

    @Test
    fun existingDirectoryIsKeptAsStart() {
        val dir = Files.createTempDirectory("codeshield-pick").toFile()
        try {
            assertEquals(dir.absolutePath, resolveStartDirectory(dir.absolutePath, "/nonexistent-home").absolutePath)
        } finally {
            dir.delete()
        }
    }

    @Test
    fun blankInputFallsBackToHome() {
        assertEquals(File("/fake/home").path, resolveStartDirectory("", "/fake/home").path)
        assertEquals(File("/fake/home").path, resolveStartDirectory("   ", "/fake/home").path)
    }

    @Test
    fun nonexistentPathFallsBackToHome() {
        val resolved = resolveStartDirectory("/nonexistent-dir-xyz", System.getProperty("user.home"))
        assertTrue(resolved.isDirectory, "fallback must be an existing directory: $resolved")
    }

    @Test
    fun filePathFallsBackToHome() {
        val file = Files.createTempFile("codeshield-pick", ".tmp").toFile()
        try {
            assertEquals(File("/fake/home").path, resolveStartDirectory(file.absolutePath, "/fake/home").path)
        } finally {
            file.delete()
        }
    }
}
