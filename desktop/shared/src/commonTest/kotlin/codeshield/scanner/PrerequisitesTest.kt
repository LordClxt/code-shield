package codeshield.scanner

import kotlin.test.Test
import kotlin.test.assertTrue

class PrerequisitesTest {

    @Test
    fun everyKnownBinaryHasAnInstallHint() {
        for (binary in listOf("semgrep", "gitleaks", "dependency-check.sh")) {
            val hint = Prerequisites.hintFor(binary)
            assertTrue(hint.contains("brew"), "hint for $binary should mention brew: $hint")
        }
    }

    @Test
    fun unknownBinaryFallsBackToGenericHint() {
        val hint = Prerequisites.hintFor("some-new-scanner")
        assertTrue(hint.contains("some-new-scanner"), hint)
    }
}
