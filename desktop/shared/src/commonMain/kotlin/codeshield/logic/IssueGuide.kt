package codeshield.logic

import codeshield.model.Finding
import codeshield.model.Severity
import codeshield.model.Category

/**
 * Explication locale du type d'issue (100% offline).
 * Répond à : "c'est quoi comme faille, pourquoi c'est grave, que faire ?"
 * Aucun réseau, aucune donnée envoyée — inféré depuis tool + ruleId + message.
 */
data class IssueExplanation(
    val category: String,
    val what: String,
    val why: String,
    val fix: String,
    val references: List<String> = emptyList(),
)

fun severityBlurb(severity: Severity): String = when (severity) {
    Severity.CRITICAL -> "CRITICAL — exploitable à distance, impact direct (RCE, exfiltration, takeover). À corriger en priorité."
    Severity.HIGH -> "HIGH — risque élevé (secret exposé, injection probable). À corriger avant merge/release."
    Severity.MEDIUM -> "MEDIUM — risque modéré ou conditionnel. À planifier rapidement."
    Severity.LOW -> "LOW — hygiène / durcissement. À corriger quand possible."
}

fun explainFinding(finding: Finding): IssueExplanation = when (finding.effectiveCategory) {
    Category.SECRET -> explainGitleaks(finding)
    Category.SCA -> explainDependency(finding)
    Category.IAC -> IssueExplanation(
        category = "Mauvaise configuration d'infrastructure (IaC)",
        what = "La règle ${finding.ruleId} (${finding.title}) signale une configuration risquée dans ${finding.filePath}.",
        why = "Une configuration permissive (bucket public, conteneur root, port ouvert, chiffrement absent) expose directement l'infrastructure.",
        fix = "1. Lisez la règle pour connaître la valeur attendue.\n2. Corrigez la ressource au plus près de la ligne indiquée (moindre privilège, chiffrement, accès restreint).\n3. Risque accepté : commentaire de suppression trivy (`trivy:ignore`) avec justification.",
        references = listOf("https://avd.aquasec.com/"),
    )
    Category.SAST -> explainSemgrep(finding)
}

private fun explainDependency(finding: Finding): IssueExplanation {
    val pkg = listOfNotNull(finding.packageName, finding.installedVersion).joinToString("@").ifBlank { "la dépendance" }
    val upgrade = finding.fixedVersion?.let { "2. Montez $pkg vers $it (ou la dernière version stable compatible)." }
        ?: "2. Aucune version corrigée connue : alternative, workaround documenté ou suppression de la dépendance."
    return IssueExplanation(
        category = "Dépendance vulnérable (SCA)",
        what = "$pkg est concernée par ${finding.ruleId}.",
        why = "Une dépendance vulnérable hérite des CVE publiées : RCE, DoS ou fuite selon la CVE. Le risque dépend de l'usage réel de la fonction vulnérable.",
        fix = "1. Repérez la déclaration dans ${finding.filePath}.\n$upgrade\n3. Régénérez le lockfile, lancez les tests, relancez le scan.",
        references = listOf("https://osv.dev/vulnerability/${finding.ruleId}", "https://nvd.nist.gov/vuln/detail/${finding.ruleId}"),
    )
}

private fun explainGitleaks(finding: Finding): IssueExplanation {
    val rule = finding.ruleId.lowercase()
    val (category, what) = when {
        "github" in rule || "gitlab" in rule || "pat" in rule ->
            "Secret exposé — token Git" to "Token Git (ex. ${finding.ruleId}) détecté dans ${finding.filePath}. Un token permet de cloner/pousser du code et d'accéder aux CI/secrets."
        "aws" in rule ->
            "Secret exposé — clé cloud AWS" to "Clé AWS détectée (${finding.ruleId}). Elle donne accès aux buckets, instances et facturation cloud."
        "private" in rule || "rsa" in rule || "ssh" in rule || "pgp" in rule ->
            "Secret exposé — clé privée" to "Clé privée détectée (${finding.ruleId}). Quiconque la lit peut usurper l'identité (SSH, TLS, signature)."
        "slack" in rule || "discord" in rule || "telegram" in rule ->
            "Secret exposé — webhook/token messagerie" to "Token de messagerie (${finding.ruleId}) : permet d'envoyer/lire des messages et d'exfiltrer via webhooks."
        "stripe" in rule || "paypal" in rule || "api" in rule || "key" in rule || "token" in rule || "password" in rule || "secret" in rule ->
            "Secret exposé — clé API / mot de passe" to "Credential détectée (${finding.ruleId}) : clé API ou mot de passe en clair. Exploitable dès que le repo fuite (clone, log, artefact)."
        else ->
            "Secret exposé" to "Motif sensible (${finding.ruleId}) détecté. Gitleaks signale un pattern à forte entropie ou une règle connue."
    }
    return IssueExplanation(
        category = category,
        what = what,
        why = "Un secret commité reste dans l'historique git même après suppression du fichier. Scanneurs publics, logs CI et artefacts l'exposent. Impact : takeover, exfiltration, coûts cloud.",
        fix = "1. Révoquez le secret immédiatement (console fournisseur).\n2. Purgez l'historique (BFG / `git filter-repo`) + force-push.\n3. Externalisez : variables d'environnement, vault (1Password, Vault, AWS Secrets).\n4. Ajoutez un pre-commit gitleaks pour bloquer les futurs commits.",
        references = listOf("https://gitleaks.io/", "https://owasp.org/www-community/controls/Secure_Storage"),
    )
}

private fun explainSemgrep(finding: Finding): IssueExplanation {
    val rule = finding.ruleId.lowercase()
    val title = finding.title
    fun base(category: String, what: String, why: String, fix: String, refs: List<String>): IssueExplanation =
        IssueExplanation(category, what, why, fix, refs)

    return when {
        "exec" in rule || "eval" in rule || "command-injection" in rule || "os-command" in rule ->
            base(
                "Injection de commande (SAST)",
                "La règle `$title` (${finding.ruleId}) détecte une exécution de commande dynamique (exec/eval/system).",
                "Si une entrée utilisateur atteint cet appel, un attaquant exécute du code OS/serveur (RCE). C'est la classe la plus critique.",
                "1. Évitez exec/eval.\n2. Si besoin shell : liste blanche d'args, `shlex.quote`, subprocess sans `shell=True`.\n3. Validez le input (allowlist) + moindre privilège.",
                listOf("https://owasp.org/www-community/attacks/Command_Injection", "https://semgrep.dev/docs/"),
            )
        "sql" in rule || "sqli" in rule || "injection" in rule ->
            base(
                "Injection SQL (SAST)",
                "La règle `$title` signale une requête SQL construite par concaténation.",
                "Un attaquant peut lire/modifier la BDD, bypass auth, voire RCE selon le driver.",
                "1. Requêtes paramétrées / ORM (placeholders, pas de f-string).\n2. Validez le typage des entrées.\n3. Compte DB à privilèges minimaux.",
                listOf("https://owasp.org/www-community/attacks/SQL_Injection"),
            )
        "xss" in rule || "cross-site" in rule || "innerhtml" in rule || "dangerously" in rule ->
            base(
                "Cross-Site Scripting XSS (SAST)",
                "La règle `$title` signale un rendu HTML/JS non échappé.",
                "Permet vol de session, keylogging, phishing interne via le navigateur des victimes.",
                "1. Échappez par défaut (templates auto-escape).\n2. `textContent` plutôt que `innerHTML`.\n3. CSP stricte.",
                listOf("https://owasp.org/www-community/attacks/xss/"),
            )
        "ssrf" in rule || "request" in rule && ("url" in rule || "fetch" in rule) ->
            base(
                "SSRF (SAST)",
                "La règle `$title` signale une requête réseau vers une URL contrôlable.",
                "Permet de sonder le réseau interne (metadata cloud 169.254.169.254), exfiltrer.",
                "1. Allowlist de domaines.\n2. Bloquez IP privées/ranges réservées.\n3. Timeout + pas de follow-redirect aveugle.",
                listOf("https://owasp.org/www-community/attacks/Server_Side_Request_Forgery"),
            )
        "path" in rule || "traversal" in rule || "open(" in rule || "file" in rule ->
            base(
                "Path traversal / accès fichier (SAST)",
                "La règle `$title` signale un accès fichier avec chemin dynamique.",
                "Permet lecture/écriture hors racine (`../../etc/passwd`), voire overwrite.",
                "1. Normalisez + `basename`, confine à un dossier (`resolve` + startswith).\n2. Allowlist d'extensions.\n3. Pas de concat directe user-input.",
                listOf("https://owasp.org/www-community/attacks/Path_Traversal"),
            )
        "deserial" in rule || "pickle" in rule || "yaml.load" in rule || "marshmallow" in rule ->
            base(
                "Désérialisation non sûre (SAST)",
                "La règle `$title` signale un `load`/`pickle` sur données non fiables.",
                "Désérialiser = exécution de code (pickle, PyYAML sans SafeLoader).",
                "1. `yaml.safe_load`, JSON, jamais pickle sur input réseau.\n2. Signez/chiffrez si persistance nécessaire.",
                listOf("https://owasp.org/www-community/vulnerabilities/Deserialization_of_untrusted_data"),
            )
        "hardcoded" in rule || "password" in rule || "secret" in rule || "key" in rule || "token" in rule ->
            base(
                "Credential en dur (SAST)",
                "La règle `$title` signale un secret codé en dur.",
                "Visible dans git, images, logs. Même impact qu'un leak gitleaks.",
                "1. Env vars / vault.\n2. Révoquez + purgez l'historique.\n3. Pre-commit semgrep+gitleaks.",
                listOf("https://owasp.org/www-community/controls/Secure_Storage"),
            )
        "crypto" in rule || "md5" in rule || "sha1" in rule || "random" in rule || "ssl" in rule || "tls" in rule || "verify=false" in rule ->
            base(
                "Crypto / TLS fragile (SAST)",
                "La règle `$title` signale un algo faible ou une vérification désactivée.",
                "Hash cassés (MD5/SHA1), RNG prévisible ou TLS non vérifié → interception, forgery.",
                "1. SHA-256+/bcrypt/argon2, `secrets` pour tokens.\n2. `verify=True`, TLS 1.2+.\n3. Suivez les recos OWASP Crypto.",
                listOf("https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html"),
            )
        "auth" in rule || "jwt" in rule || "session" in rule || "password" in rule ->
            base(
                "Authentification / session (SAST)",
                "La règle `$title` touche à l'auth (JWT, session, mot de passe).",
                "Mauvaise gestion = bypass auth, fixation/ vol de session, brute-force.",
                "1. Libs éprouvées, JWT vérifié (alg, exp, iss).\n2. Hash argon2/bcrypt, rate-limit, MFA.\n3. Cookies HttpOnly/Secure/SameSite.",
                listOf("https://owasp.org/www-project-top-ten/"),
            )
        else ->
            base(
                "Faiblesse code (SAST Semgrep)",
                "La règle `$title` (${finding.ruleId}) correspond au pattern Semgrep `${finding.ruleId}`.",
                "Semgrep signale un pattern à risque (sécurité, correctness ou best-practice). Lisez le message outil ci-dessous : il donne la raison exacte du match.",
                "1. Lisez le message + la ligne mise en cause.\n2. Appliquez le fix suggéré par la règle (doc Semgrep).\n3. Si faux positif : `// nosemgrep: ${finding.ruleId}` avec justification.",
                listOf("https://semgrep.dev/docs/", "https://owasp.org/www-project-top-ten/"),
            )
    }
}
