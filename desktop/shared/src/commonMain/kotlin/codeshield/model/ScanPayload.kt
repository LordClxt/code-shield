package codeshield.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Payload POST /api/v1/sync/submit. Métadonnées uniquement.
 * Les noms JSON (snake_case) correspondent exactement à
 * contracts/openapi.yaml#/components/schemas/SyncSubmitRequest.
 */
@Serializable
data class ScanPayload(
    @SerialName("project_id") val projectId: Int,
    @SerialName("findings") val findings: List<Finding>,
    @SerialName("commit_sha") val commitSha: String? = null,
    @SerialName("branch") val branch: String? = null,
) {
    companion object {
        /** explicitNulls=false : les champs optionnels absents ne sont pas envoyés. */
        val json: Json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

        fun toJson(payload: ScanPayload): String = json.encodeToString(payload)

        fun fromJson(raw: String): ScanPayload = json.decodeFromString(raw)
    }
}
