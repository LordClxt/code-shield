package codeshield.tools

import codeshield.model.ToolName
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class JvmToolInstallerTest {

    private fun installerInTemp(): Pair<JvmToolInstaller, java.io.File> {
        val dir = Files.createTempDirectory("codeshield-tools").toFile()
        return JvmToolInstaller(toolsDir = dir, locator = { null }) to dir
    }

    @Test
    fun emptyToolsDirMeansEverythingMissing() {
        val (installer, dir) = installerInTemp()
        try {
            assertNull(installer.installedPath(ToolName.GITLEAKS))
            assertNull(installer.installedPath(ToolName.SEMGREP))
            assertNull(installer.installedPath(ToolName.DEPENDENCY_CHECK))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun presentBinaryIsDetectedAsInstalled() {
        val (installer, dir) = installerInTemp()
        try {
            val fake = dir.resolve("gitleaks").apply { writeText("#!/bin/sh\necho hi\n") }
            assertEquals(fake.absolutePath, installer.installedPath(ToolName.GITLEAKS))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sha256Vector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun zipRoundTrip() {
        val bytes = zipOf(mapOf("gitleaks" to "#!/bin/sh".toByteArray()))
        val dest = Files.createTempDirectory("codeshield-zip").toFile()
        try {
            extractZip(bytes, dest)
            assertEquals("#!/bin/sh", dest.resolve("gitleaks").readText())
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun zipSlipIsRejected() {
        val bytes = zipOf(mapOf("../evil.sh" to "x".toByteArray()))
        val dest = Files.createTempDirectory("codeshield-zipslip").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { extractZip(bytes, dest) }
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun tarGzRoundTrip() {
        val bytes = tarGzOf(mapOf("gitleaks" to "#!/bin/sh".toByteArray()))
        val dest = Files.createTempDirectory("codeshield-tgz").toFile()
        try {
            extractTarGz(bytes, dest)
            assertEquals("#!/bin/sh", dest.resolve("gitleaks").readText())
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun tarSlipIsRejected() {
        val bytes = tarGzOf(mapOf("../../evil.sh" to "x".toByteArray()))
        val dest = Files.createTempDirectory("codeshield-tarslip").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { extractTarGz(bytes, dest) }
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun platformDetectionIsSane() {
        val (installer, dir) = installerInTemp()
        try {
            assertNotNull(installer.osKind())
            assertNotNull(installer.arch())
        } finally {
            dir.deleteRecursively()
        }
    }

    // -- Fabriques d'archives (tests uniquement) ----------------------------

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun tarGzOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { gzip ->
            val tar = org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(gzip)
            tar.use { archive ->
                for ((name, content) in entries) {
                    val entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry(name)
                    entry.size = content.size.toLong()
                    archive.putArchiveEntry(entry)
                    archive.write(content)
                    archive.closeArchiveEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
