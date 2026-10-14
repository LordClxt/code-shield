package codeshield.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sévérité alignée avec le backend (Pydantic Severity) et le frontend (Severity).
 * Ne jamais renommer les valeurs : elles sont sérialisées telles quelles en JSON.
 */
@Serializable
enum class Severity {
    @SerialName("CRITICAL") CRITICAL,
    @SerialName("HIGH") HIGH,
    @SerialName("MEDIUM") MEDIUM,
    @SerialName("LOW") LOW,
}
