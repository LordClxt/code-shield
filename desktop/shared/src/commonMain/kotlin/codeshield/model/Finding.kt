package codeshield.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Une faille détectée localement. MÉTADONNÉES UNIQUEMENT :
 * aucun champ de code source brut (source_code, snippet, ...) ne doit
 * jamais être ajouté ici — directive Sécurité Intellectuelle.
 *
 * Contrat : contracts/openapi.yaml#/components/schemas/Finding.
 */
@Serializable
data class Finding(
    @SerialName("tool") val tool: ToolName,
    @SerialName("rule_id") val ruleId: String,
    @SerialName("file_path") val filePath: String,
    @SerialName("line_number") val lineNumber: Int,
    @SerialName("severity") val severity: Severity,
    @SerialName("title") val title: String,
    @SerialName("message") val message: String? = null,
    @SerialName("end_line") val endLine: Int? = null,
    @SerialName("column") val column: Int? = null,
    @SerialName("end_column") val endColumn: Int? = null,
    @SerialName("category") val category: Category? = null,
    @SerialName("cwe") val cwe: List<String> = emptyList(),
    @SerialName("owasp") val owasp: List<String> = emptyList(),
    @SerialName("remediation_key") val remediationKey: String? = null,
    @SerialName("package") val packageName: String? = null,
    @SerialName("installed_version") val installedVersion: String? = null,
    @SerialName("fixed_version") val fixedVersion: String? = null,
    /** sha256 calculé localement (cf. Fingerprint) : jamais le texte de la ligne. */
    @SerialName("fingerprint") val fingerprint: String? = null,
) {
    init {
        require(ruleId.isNotBlank()) { "ruleId must not be blank" }
        require(filePath.isNotBlank()) { "filePath must not be blank" }
        require(lineNumber >= 1) { "lineNumber must be >= 1" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(endLine == null || endLine >= lineNumber) { "endLine must be >= lineNumber" }
    }

    /** Catégorie effective (déduite de l'outil si non fournie, comme le backend). */
    val effectiveCategory: Category get() = category ?: tool.defaultCategory

    /** Dernière ligne concernée (plage surlignée dans le visualiseur). */
    val lastLine: Int get() = endLine ?: lineNumber

    /** Clé d'identité exacte du moteur différentiel backend (outil, règle, fichier, ligne). */
    fun identityKey(): FindingKey = FindingKey(tool, ruleId, filePath, lineNumber)
}

/** Clé d'identité exacte, partagée avec SyncDiff et le backend. */
@Serializable
data class FindingKey(
    val tool: ToolName,
    val ruleId: String,
    val filePath: String,
    val lineNumber: Int,
)
