package codeshield.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanPayloadSerializationTest {

    private fun sample() = ScanPayload(
        projectId = 1,
        findings = listOf(
            Finding(
                tool = ToolName.SEMGREP,
                ruleId = "python.lang.security.audit.dangerous-exec",
                filePath = "src/app.py",
                lineNumber = 42,
                severity = Severity.HIGH,
                title = "Dangerous exec",
                message = "Avoid exec with user input",
            ),
        ),
    )

    @Test
    fun roundTripPreservesAllFields() {
        val restored = ScanPayload.fromJson(ScanPayload.toJson(sample()))
        assertEquals(sample(), restored)
    }

    @Test
    fun jsonUsesSnakeCaseContract() {
        val json = ScanPayload.toJson(sample())
        assertTrue(json.contains("\"project_id\":1"), json)
        assertTrue(json.contains("\"rule_id\""), json)
        assertTrue(json.contains("\"file_path\":\"src/app.py\""), json)
        assertTrue(json.contains("\"line_number\":42"), json)
    }

    @Test
    fun jsonNeverContainsSourceCode() {
        // Sécurité Intellectuelle : le payload sérialisé ne doit contenir
        // aucune clé de code brut, même par accident.
        val json = ScanPayload.toJson(sample())
        for (forbidden in listOf("source_code", "snippet", "\"code\"", "\"source\"", "raw_code")) {
            assertFalse(json.contains(forbidden), "payload leaks $forbidden: $json")
        }
    }

    @Test
    fun findingValidationRejectsInvalidLine() {
        var failed = false
        try {
            sample().findings.first().copy(lineNumber = 0)
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed, "lineNumber=0 must be rejected")
    }
}
