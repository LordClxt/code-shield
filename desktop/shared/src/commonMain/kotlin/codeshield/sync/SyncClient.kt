package codeshield.sync

import codeshield.model.ScanPayload
import codeshield.model.Severity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Réponse POST /api/v1/sync/submit (contracts/openapi.yaml#/components/schemas/SyncSubmitResponse). */
@Serializable
data class SyncResponse(
    @SerialName("scan_id") val scanId: Int,
    @SerialName("created") val created: Int,
    @SerialName("touched") val touched: Int,
    @SerialName("resolved") val resolved: Int,
    @SerialName("open_total") val openTotal: Int,
    @SerialName("score") val score: Int,
    @SerialName("grade") val grade: String,
    /** Failles résolues (ou marquées corrigées) qui réapparaissent. */
    @SerialName("reopened") val reopened: Int = 0,
    @SerialName("open_counts") val openCounts: Map<Severity, Int> = emptyMap(),
)

/** Contrat d'envoi vers le Cloud (implémenté en jvmMain via Ktor). */
interface SyncClient {
    suspend fun submit(payload: ScanPayload): SyncResponse
}
