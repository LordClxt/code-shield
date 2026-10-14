package codeshield.ui.i18n

import kotlinx.serialization.Serializable

/**
 * Langues supportées par l'interface utilisateur de CodeShield Desktop.
 */
@Serializable
enum class AppLanguage(
    val code: String,
    val label: String,
    val nativeLabel: String,
    val flag: String,
) {
    FR("fr", "French", "Français", "🇫🇷"),
    EN("en", "English", "English", "🇬🇧");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: FR
    }
}
