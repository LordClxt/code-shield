package codeshield.model

import codeshield.sync.SyncResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Alignement des types (AGENTS.md) : les fixtures de contracts/fixtures/ doivent
 * se (dé)sérialiser à l'identique côté Kotlin. Les mêmes fixtures sont testées
 * côté Go (backend-go) et TypeScript (frontend).
 */
class ContractFixturesTest {
    private fun fixture(name: String): String {
        val candidates = listOf("../../contracts/fixtures/$name", "../contracts/fixtures/$name", "contracts/fixtures/$name")
        return candidates.map(::File).first { it.isFile }.readText()
    }

    @Test
    fun syncRequestFixtureRoundTrips() {
        val raw = fixture("sync_request.json")
        val payload = ScanPayload.fromJson(raw)
        assertEquals(7, payload.projectId)
        assertEquals("main", payload.branch)
        val sql = payload.findings.first()
        assertEquals(44, sql.endLine)
        assertEquals(listOf("CWE-89"), sql.cwe)
        assertEquals(Category.SAST, sql.category)
        val sca = payload.findings.first { it.tool == ToolName.TRIVY }
        assertEquals("flask", sca.packageName)
        assertEquals("2.2.5", sca.fixedVersion)

        // Ré-encodage sans perte (les listes vides `cwe: []` ajoutées sont admises par le contrat).
        assertEquals(payload, ScanPayload.fromJson(ScanPayload.toJson(payload)))
    }

    @Test
    fun serializedPayloadOmitsAbsentOptionalFields() {
        val json = ScanPayload.toJson(ScanPayload(1, listOf(Finding(ToolName.SEMGREP, "R", "a.py", 1, Severity.LOW, "t"))))
        assertFalse(json.contains("null"), json)
        assertFalse(json.contains("fingerprint"), json)
    }

    @Test
    fun syncResponseFixtureDecodes() {
        val r = Json { ignoreUnknownKeys = true }.decodeFromString<SyncResponse>(fixture("sync_response.json"))
        assertEquals(0, r.reopened)
        assertEquals(2, r.openCounts[Severity.HIGH])
        assertEquals("B", r.grade)
    }

    @Test
    fun everyKotlinEnumValueExistsInTheOpenApiContract() {
        val spec = listOf("../../contracts/openapi.yaml", "../contracts/openapi.yaml").map(::File).first { it.isFile }.readText()
        fun enumLine(schema: String) = spec.substringAfter("    $schema:\n").substringAfter("enum: [").substringBefore("]")
        assertEquals(ToolName.entries.map { it.name }, enumLine("ToolName").split(", "))
        assertEquals(Severity.entries.map { it.name }, enumLine("Severity").split(", "))
        assertEquals(Category.entries.map { it.name }, enumLine("Category").split(", "))
        assertTrue(Json.parseToJsonElement(fixture("sync_request.json")).jsonObject.containsKey("findings"))
    }
}
