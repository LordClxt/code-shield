package codeshield.scanner

/**
 * Pré-vérifications avant tout scan (cause du crash `Cannot run program "semgrep"` :
 * l'IOException de ProcessBuilder remontait non catchée jusqu'au dialogue natif).
 *
 * - Dossier projet inexistant -> erreur inline, aucun processus lancé.
 * - Binaire manquant -> moteur ignoré avec sa commande d'installation, pas de crash.
 */
object Prerequisites {
    /** Commande d'installation par binaire. */
    val installHints: Map<String, String> = ScannerEngines.all.associate { it.binary to it.installHint }

    fun hintFor(binary: String): String =
        installHints[binary] ?: "installez `$binary` puis relancez le scan"
}
