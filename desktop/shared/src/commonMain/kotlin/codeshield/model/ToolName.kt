package codeshield.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Moteur d'analyse. Aligné avec le backend (domain.Tool) et contracts/openapi.yaml.
 * BANDIT et CHECKOV ne sont plus produits (moteurs retirés) : conservés pour relire
 * l'historique local et les failles déjà synchronisées.
 */
@Serializable
enum class ToolName(val defaultCategory: Category) {
    @SerialName("SEMGREP") SEMGREP(Category.SAST),
    @SerialName("GITLEAKS") GITLEAKS(Category.SECRET),
    @SerialName("DEPENDENCY_CHECK") DEPENDENCY_CHECK(Category.SCA),
    @SerialName("TRIVY") TRIVY(Category.SCA),
    @SerialName("BANDIT") BANDIT(Category.SAST),
    @SerialName("OSV_SCANNER") OSV_SCANNER(Category.SCA),
    @SerialName("CHECKOV") CHECKOV(Category.IAC),
}

/** Famille de faille. Alignée avec le backend (domain.Category). */
@Serializable
enum class Category {
    @SerialName("SAST") SAST,
    @SerialName("SECRET") SECRET,
    @SerialName("SCA") SCA,
    @SerialName("IAC") IAC,
}
