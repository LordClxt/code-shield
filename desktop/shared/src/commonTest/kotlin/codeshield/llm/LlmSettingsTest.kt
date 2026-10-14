package codeshield.llm

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.ToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmSettingsTest {
    @Test
    fun defaultsAreDisabled() {
        val s = LlmSettings()
        assertEquals(LlmProvider.NONE, s.provider)
        assertFalse(s.enabled)
    }

    @Test
    fun modelsPerProvider() {
        assertTrue(LlmModels.defaultsFor(LlmProvider.OPENAI).contains("gpt-4o-mini"))
        assertTrue(LlmModels.defaultsFor(LlmProvider.GEMINI).any { it.contains("gemini") })
        assertTrue(LlmModels.defaultsFor(LlmProvider.ANTHROPIC).any { it.contains("claude") })
        assertTrue(LlmModels.defaultsFor(LlmProvider.OLLAMA).isNotEmpty())
        assertFalse(LlmModels.needsKey(LlmProvider.OLLAMA))
        assertTrue(LlmModels.needsKey(LlmProvider.OPENAI))
    }

    @Test
    fun triagePromptIsMetadataOnly() {
        val f = Finding(ToolName.SEMGREP, "r", "a.py", 1, Severity.HIGH, "t", "msg")
        val p = buildTriagePrompt("/proj", listOf(f))
        assertTrue(p.contains("METADONNEES"))
        assertFalse(p.contains("source_code"))
    }

    @Test
    fun redactedHidesKey() {
        val s = LlmSettings(apiKey = "sk-123456789")
        assertTrue(s.redacted().endsWith("6789"))
        assertFalse(s.redacted().contains("sk-1234"))
    }

    @Test
    fun maxTokensDefaultsHighEnoughAndIsCoerced() {
        // Défaut suffisant pour une réponse complète (avant : 1200 -> coupures nettes).
        assertEquals(4096, LlmSettings().maxTokens)
        assertEquals(256, LlmSettings(maxTokens = 0).coercedMaxTokens())
        assertEquals(32000, LlmSettings(maxTokens = 999999).coercedMaxTokens())
    }
}
