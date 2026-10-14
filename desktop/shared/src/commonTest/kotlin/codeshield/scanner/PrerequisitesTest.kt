package codeshield.scanner

import kotlin.test.Test
import kotlin.test.assertTrue

class PrerequisitesTest {

    @Test
    fun everyKnownBinaryHasAnInstallHint() {
        for (engine in ScannerEngines.all) {
            val hint = Prerequisites.hintFor(engine.binary)
            assertTrue(hint == engine.installHint && hint.isNotBlank(), "hint for ${engine.binary}: $hint")
        }
    }

    @Test
    fun noInstallHintRequiresPythonForDefaultEngines() {
        // Les moteurs actifs par défaut s'installent sans Python ni pip.
        for (engine in ScannerEngines.all.filter { it.defaultEnabled }) {
            assertTrue("pip" !in engine.installHint, "${engine.label}: ${engine.installHint}")
        }
    }

    @Test
    fun unknownBinaryFallsBackToGenericHint() {
        val hint = Prerequisites.hintFor("some-new-scanner")
        assertTrue(hint.contains("some-new-scanner"), hint)
    }
}
