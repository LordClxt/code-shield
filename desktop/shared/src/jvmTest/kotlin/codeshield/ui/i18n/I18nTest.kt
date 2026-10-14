package codeshield.ui.i18n

import codeshield.llm.LlmSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class I18nTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun testLanguageEnumAndFromCode() {
        assertEquals("fr", AppLanguage.FR.code)
        assertEquals("Français", AppLanguage.FR.nativeLabel)
        assertEquals("🇫🇷", AppLanguage.FR.flag)

        assertEquals("en", AppLanguage.EN.code)
        assertEquals("English", AppLanguage.EN.nativeLabel)
        assertEquals("🇬🇧", AppLanguage.EN.flag)

        assertEquals(AppLanguage.FR, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.FR, AppLanguage.fromCode("FR"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("EN"))
        assertEquals(AppLanguage.FR, AppLanguage.fromCode("invalid_or_unknown"))
    }

    @Test
    fun testStringsForReturnsCorrectDictionary() {
        val fr = stringsFor(AppLanguage.FR)
        val en = stringsFor(AppLanguage.EN)

        assertEquals(AppLanguage.FR, fr.language)
        assertEquals(AppLanguage.EN, en.language)

        assertEquals("Paramètres", fr.settingsTitle)
        assertEquals("Settings", en.settingsTitle)

        assertEquals("Vue d'ensemble", fr.overviewTitle)
        assertEquals("Overview", en.overviewTitle)

        assertEquals("Vulnérabilités détectées", fr.findingsTitle)
        assertEquals("Detected findings", en.findingsTitle)

        assertEquals("Pipeline d'exécution", fr.pipelineTitle)
        assertEquals("Pipeline Console", en.pipelineTitle)

        assertEquals("Synchronisation Cloud", fr.syncTitle)
        assertEquals("Cloud Synchronization", en.syncTitle)

        assertEquals("Pentest Backend", fr.pentestTitle)
        assertEquals("Backend Pentest", en.pentestTitle)
    }

    @Test
    fun testLlmSettingsSerializationWithLanguage() {
        val defaultSettings = LlmSettings(language = AppLanguage.FR)
        val encodedFr = json.encodeToString(defaultSettings)
        assertTrue(encodedFr.contains("\"language\":\"FR\""))

        val decodedFr = json.decodeFromString<LlmSettings>(encodedFr)
        assertEquals(AppLanguage.FR, decodedFr.language)

        val enSettings = defaultSettings.copy(language = AppLanguage.EN)
        val encodedEn = json.encodeToString(enSettings)
        assertTrue(encodedEn.contains("\"language\":\"EN\""))

        val decodedEn = json.decodeFromString<LlmSettings>(encodedEn)
        assertEquals(AppLanguage.EN, decodedEn.language)

        // Backward compatibility: JSON missing "language" defaults to FR
        val legacyJson = """{"enabled":true,"provider":"OLLAMA","model":"llama3:8b"}"""
        val decodedLegacy = json.decodeFromString<LlmSettings>(legacyJson)
        assertEquals(AppLanguage.FR, decodedLegacy.language)
    }

    @Test
    fun testParameterizedStringsNonBlank() {
        val fr = stringsFor(AppLanguage.FR)
        val en = stringsFor(AppLanguage.EN)

        assertTrue(fr.sidebarEnginesReady(2, 3).contains("2/3"))
        assertTrue(en.sidebarEnginesReady(2, 3).contains("2/3"))

        assertTrue(fr.pentestStatusOnline(8000).contains("8000"))
        assertTrue(en.pentestStatusOnline(8000).contains("8000"))

        assertTrue(fr.settingsApiKeyDesc("OLLAMA").contains("OLLAMA"))
        assertTrue(en.settingsApiKeyDesc("OLLAMA").contains("OLLAMA"))

        assertTrue(fr.pentestActiveTarget("http://127.0.0.1:8000").contains("http://127.0.0.1:8000"))
        assertTrue(en.pentestActiveTarget("http://127.0.0.1:8000").contains("http://127.0.0.1:8000"))
    }

    @Test
    fun testKeyTranslationsDistinctBetweenFrAndEn() {
        val fr = stringsFor(AppLanguage.FR)
        val en = stringsFor(AppLanguage.EN)

        assertNotEquals(fr.sidebarOverview, en.sidebarOverview)
        assertNotEquals(fr.sidebarFindings, en.sidebarFindings)
        assertNotEquals(fr.sidebarSettings, en.sidebarSettings)
        assertNotEquals(fr.overviewRunScan, en.overviewRunScan)
        assertNotEquals(fr.settingsThemeLabel, en.settingsThemeLabel)
    }
}
