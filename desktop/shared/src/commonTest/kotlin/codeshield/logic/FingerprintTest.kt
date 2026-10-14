package codeshield.logic

import codeshield.model.Category
import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FingerprintTest {
    private fun f(line: Int, rule: String = "R", tool: ToolName = ToolName.SEMGREP, path: String = "a.py", cat: Category? = null, pkg: String? = null) =
        Finding(tool, rule, path, line, Severity.HIGH, "t", category = cat, packageName = pkg)

    private val v1 = listOf("import os", "", "os.system(cmd)  ", "print('x')")
    private val v2 = listOf("import os", "# nouveau commentaire", "", "", "   os.system(cmd)", "print('x')")

    @Test
    fun sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }

    @Test
    fun fingerprintSurvivesLineShiftAndWhitespace() {
        val a = Fingerprint.apply(listOf(f(3)), "project:1") { v1 }.single().fingerprint
        val b = Fingerprint.apply(listOf(f(5)), "project:1") { v2 }.single().fingerprint
        assertEquals(a, b)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(a!!))
    }

    @Test
    fun saltAndRuleChangeTheFingerprint() {
        val base = Fingerprint.apply(listOf(f(3)), "project:1") { v1 }.single().fingerprint
        assertNotEquals(base, Fingerprint.apply(listOf(f(3)), "project:2") { v1 }.single().fingerprint)
        assertNotEquals(base, Fingerprint.apply(listOf(f(3, rule = "R2")), "project:1") { v1 }.single().fingerprint)
    }

    @Test
    fun identicalLinesGetDistinctOccurrences() {
        val lines = listOf("eval(x)", "eval(x)")
        val out = Fingerprint.apply(listOf(f(2), f(1)), "s") { lines }
        assertNotEquals(out[0].fingerprint, out[1].fingerprint)
    }

    @Test
    fun secretsAreNeverHashedWithTheirLine() {
        var read = false
        Fingerprint.apply(listOf(f(1, tool = ToolName.GITLEAKS, cat = Category.SECRET)), "s") { read = true; listOf("KEY=AKIA...") }
        assertTrue(!read, "la ligne d'un secret ne doit même pas être lue")
    }

    @Test
    fun scaAnchorsOnPackageNotLine() {
        val a = Fingerprint.apply(listOf(f(1, tool = ToolName.TRIVY, cat = Category.SCA, pkg = "flask")), "s") { listOf("flask==2.2") }
        val b = Fingerprint.apply(listOf(f(1, tool = ToolName.TRIVY, cat = Category.SCA, pkg = "flask")), "s") { listOf("# deps", "flask==2.2") }
        assertEquals(a.single().fingerprint, b.single().fingerprint)
    }

    @Test
    fun unreadableFileStillYieldsAFingerprint() {
        val out = Fingerprint.apply(listOf(f(3)), "s") { throw IllegalStateException("gone") }
        assertTrue(out.single().fingerprint!!.length == 64)
    }
}
