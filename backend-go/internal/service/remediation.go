package service

import (
	"fmt"
	"slices"
	"strings"

	"codeshield/backend/internal/domain"
)

// Guidance : explication et correctif d'une faille, porté depuis
// desktop/shared/.../logic/IssueGuide.kt. Inféré uniquement depuis les
// métadonnées (outil, règle, CWE) — aucun code utilisateur.
type Guidance struct {
	Category   string   `json:"category"`
	What       string   `json:"what"`
	Why        string   `json:"why"`
	Fix        []string `json:"fix"`
	References []string `json:"references"`
}

func SeverityBlurb(s domain.Severity) string {
	switch s {
	case domain.SeverityCritical:
		return "CRITICAL — exploitable à distance, impact direct (RCE, exfiltration, takeover). À corriger en priorité."
	case domain.SeverityHigh:
		return "HIGH — risque élevé (secret exposé, injection probable). À corriger avant merge/release."
	case domain.SeverityMedium:
		return "MEDIUM — risque modéré ou conditionnel. À planifier rapidement."
	default:
		return "LOW — hygiène / durcissement. À corriger quand possible."
	}
}

type rule struct {
	keywords []string
	cwes     []string
	g        func(v *domain.Vulnerability) Guidance
}

func has(s string, kws ...string) bool {
	for _, k := range kws {
		if strings.Contains(s, k) {
			return true
		}
	}
	return false
}

func secretGuidance(v *domain.Vulnerability) Guidance {
	r := strings.ToLower(v.RuleID)
	cat, what := "Secret exposé", fmt.Sprintf("Motif sensible (%s) détecté dans %s.", v.RuleID, v.FilePath)
	switch {
	case has(r, "github", "gitlab", "pat"):
		cat, what = "Secret exposé — token Git", fmt.Sprintf("Token Git (%s) détecté dans %s : il permet de cloner/pousser du code et d'accéder aux CI.", v.RuleID, v.FilePath)
	case has(r, "aws"):
		cat, what = "Secret exposé — clé cloud AWS", fmt.Sprintf("Clé AWS détectée (%s) : accès aux buckets, instances et facturation.", v.RuleID)
	case has(r, "private", "rsa", "ssh", "pgp"):
		cat, what = "Secret exposé — clé privée", fmt.Sprintf("Clé privée détectée (%s) : usurpation d'identité possible (SSH, TLS, signature).", v.RuleID)
	case has(r, "slack", "discord", "telegram"):
		cat, what = "Secret exposé — token messagerie", fmt.Sprintf("Token de messagerie (%s) : envoi/lecture de messages, exfiltration via webhooks.", v.RuleID)
	case has(r, "stripe", "paypal", "api", "key", "token", "password", "secret"):
		cat, what = "Secret exposé — clé API / mot de passe", fmt.Sprintf("Credential détectée (%s) : clé API ou mot de passe en clair.", v.RuleID)
	}
	return Guidance{
		Category: cat, What: what,
		Why: "Un secret commité reste dans l'historique git même après suppression. Scanneurs publics, logs CI et artefacts l'exposent : takeover, exfiltration, coûts cloud.",
		Fix: []string{
			"Révoquer le secret immédiatement chez le fournisseur (c'est l'étape qui compte, même après correction du code).",
			"Remplacer la valeur en dur par une lecture de variable d'environnement ou d'un gestionnaire de secrets.",
			"Purger l'historique (git filter-repo / BFG) puis force-push si le dépôt est partagé.",
			"Ajouter un hook pre-commit gitleaks pour bloquer les récidives.",
		},
		References: []string{"https://gitleaks.io/", "https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html"},
	}
}

func scaGuidance(v *domain.Vulnerability) Guidance {
	pkg := "la dépendance signalée"
	if v.Package != nil && *v.Package != "" {
		pkg = *v.Package
		if v.InstalledVersion != nil {
			pkg += "@" + *v.InstalledVersion
		}
	}
	fix := []string{fmt.Sprintf("Identifier où %s est déclarée (manifeste ou lockfile).", pkg)}
	if v.FixedVersion != nil && *v.FixedVersion != "" {
		fix = append(fix, fmt.Sprintf("Monter la version vers %s (ou la dernière version stable compatible).", *v.FixedVersion))
	} else {
		fix = append(fix, "Aucune version corrigée connue : envisager une alternative, un workaround documenté ou la suppression de la dépendance.")
	}
	fix = append(fix, "Régénérer le lockfile, lancer les tests, puis relancer le scan pour confirmer.")
	return Guidance{
		Category: "Dépendance vulnérable (SCA)",
		What:     fmt.Sprintf("%s est concernée par %s.", pkg, v.RuleID),
		Why:      "Une dépendance vulnérable hérite des CVE publiées (RCE, DoS, fuite). Le risque dépend de l'usage réel de la fonction vulnérable.",
		Fix:      fix,
		References: []string{
			"https://osv.dev/vulnerability/" + v.RuleID,
			"https://nvd.nist.gov/vuln/detail/" + v.RuleID,
		},
	}
}

func iacGuidance(v *domain.Vulnerability) Guidance {
	return Guidance{
		Category: "Mauvaise configuration d'infrastructure (IaC)",
		What:     fmt.Sprintf("La règle %s (%s) signale une configuration risquée dans %s.", v.RuleID, v.Title, v.FilePath),
		Why:      "Une configuration permissive (bucket public, conteneur root, port ouvert, chiffrement absent) expose directement l'infrastructure en production.",
		Fix: []string{
			"Lire la règle (lien ci-dessous) pour connaître la valeur attendue.",
			"Corriger la ressource au plus près de la ligne indiquée (moindre privilège, chiffrement activé, accès restreint).",
			"Si le risque est accepté, documenter l'exception (commentaire de suppression trivy `trivy:ignore` avec justification).",
		},
		References: []string{"https://avd.aquasec.com/"},
	}
}

// Règles SAST : mots-clés sur rule_id OU CWE. L'ordre compte (premier match).
var sastRules = []rule{
	{[]string{"exec", "eval", "command-injection", "os-command", "subprocess", "b602", "b605"}, []string{"CWE-78", "CWE-94", "CWE-95"}, func(v *domain.Vulnerability) Guidance {
		return Guidance{"Injection de commande / code", fmt.Sprintf("`%s` détecte une exécution dynamique de commande ou de code.", v.Title),
			"Si une entrée utilisateur atteint cet appel, un attaquant exécute du code sur le serveur (RCE).",
			[]string{"Supprimer exec/eval ; préférer un appel de fonction explicite.", "Pour lancer un processus : liste d'arguments, jamais de shell=True ni de concaténation.", "Valider l'entrée par liste blanche."},
			[]string{"https://owasp.org/www-community/attacks/Command_Injection"}}
	}},
	{[]string{"sql", "sqli", "b608"}, []string{"CWE-89"}, func(v *domain.Vulnerability) Guidance {
		return Guidance{"Injection SQL", fmt.Sprintf("`%s` signale une requête SQL construite par concaténation.", v.Title),
			"Lecture/modification de la base, contournement d'authentification.",
			[]string{"Utiliser des requêtes paramétrées / placeholders de l'ORM.", "Ne jamais formater une requête avec f-string, + ou format().", "Compte DB à privilèges minimaux."},
			[]string{"https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html"}}
	}},
	{[]string{"xss", "cross-site", "innerhtml", "dangerously"}, []string{"CWE-79"}, func(v *domain.Vulnerability) Guidance {
		return Guidance{"Cross-Site Scripting (XSS)", fmt.Sprintf("`%s` signale un rendu HTML/JS non échappé.", v.Title),
			"Vol de session, keylogging, phishing via le navigateur des victimes.",
			[]string{"Échapper par défaut (templates auto-escape).", "Utiliser textContent plutôt qu'innerHTML ; sinon assainir (DOMPurify).", "Ajouter une CSP stricte."},
			[]string{"https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html"}}
	}},
	{[]string{"ssrf"}, []string{"CWE-918"}, func(v *domain.Vulnerability) Guidance {
		return Guidance{"SSRF", fmt.Sprintf("`%s` signale une requête réseau vers une URL contrôlable.", v.Title),
			"Accès au réseau interne et aux métadonnées cloud depuis le serveur.",
			[]string{"Liste blanche de domaines.", "Refuser les IP privées/réservées après résolution DNS.", "Timeout et pas de suivi aveugle des redirections."},
			[]string{"https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html"}}
	}},
	{[]string{"traversal", "path-injection", "b108"}, []string{"CWE-22", "CWE-23"}, func(v *domain.Vulnerability) Guidance {
		return Guidance{"Path traversal", fmt.Sprintf("`%s` signale un accès fichier avec chemin dynamique.", v.Title),
			"Lecture/écriture hors du répertoire prévu (../../etc/passwd).",
			[]string{"Résoudre le chemin canonique et vérifier qu'il reste sous la racine autorisée.", "Liste blanche de noms/extensions.", "Ne pas concaténer d'entrée utilisateur dans un chemin."},
			[]string{"https://owasp.org/www-community/attacks/Path_Traversal"}}
	}},
	{[]string{"deserial", "pickle", "yaml.load", "b301", "b506"}, []string{"CWE-502"}, func(v *domain.Vulnerability) Guidance {
		return Guidance{"Désérialisation non sûre", fmt.Sprintf("`%s` signale une désérialisation de données non fiables.", v.Title),
			"Désérialiser (pickle, YAML non sûr) peut exécuter du code arbitraire.",
			[]string{"yaml.safe_load / JSON ; jamais pickle sur une entrée externe.", "Signer les données persistées si nécessaire."},
			[]string{"https://cheatsheetseries.owasp.org/cheatsheets/Deserialization_Cheat_Sheet.html"}}
	}},
	{[]string{"hardcoded", "b105", "b106", "b107"}, []string{"CWE-798", "CWE-259"}, func(v *domain.Vulnerability) Guidance {
		g := secretGuidance(v)
		g.Category = "Credential en dur"
		return g
	}},
	{[]string{"crypto", "md5", "sha1", "insecure-hash", "random", "ssl", "tls", "verify", "b303", "b311", "b501"}, []string{"CWE-327", "CWE-328", "CWE-330", "CWE-295"}, func(v *domain.Vulnerability) Guidance {
		return Guidance{"Crypto / TLS fragile", fmt.Sprintf("`%s` signale un algorithme faible ou une vérification désactivée.", v.Title),
			"Hash cassés, RNG prévisible ou TLS non vérifié : interception, falsification.",
			[]string{"SHA-256+ pour l'intégrité, bcrypt/argon2 pour les mots de passe.", "Générateur cryptographique (secrets, crypto/rand) pour les tokens.", "Réactiver la vérification TLS (TLS 1.2+)."},
			[]string{"https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html"}}
	}},
	{[]string{"auth", "jwt", "session", "csrf"}, []string{"CWE-287", "CWE-352", "CWE-384"}, func(v *domain.Vulnerability) Guidance {
		return Guidance{"Authentification / session", fmt.Sprintf("`%s` concerne l'authentification ou la session.", v.Title),
			"Contournement d'authentification, vol ou fixation de session.",
			[]string{"Vérifier les JWT (algorithme fixé, exp, iss).", "Cookies HttpOnly/Secure/SameSite, protection CSRF.", "Rate limiting et MFA sur les points sensibles."},
			[]string{"https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html"}}
	}},
}

func Explain(v *domain.Vulnerability) Guidance {
	switch v.Category {
	case domain.CategorySecret:
		return secretGuidance(v)
	case domain.CategorySCA:
		return scaGuidance(v)
	case domain.CategoryIaC:
		return iacGuidance(v)
	}
	// La CWE (donnée structurée) prime sur les mots-clés du rule_id, qui sont
	// ambigus (ex. "execute-raw-query" est une injection SQL, pas un exec).
	for _, sr := range sastRules {
		if slices.ContainsFunc(v.CWE, func(c string) bool { return slices.Contains(sr.cwes, c) }) {
			return sr.g(v)
		}
	}
	r := strings.ToLower(v.RuleID + " " + derefStr(v.RemediationKey))
	for _, sr := range sastRules {
		if has(r, sr.keywords...) {
			return sr.g(v)
		}
	}
	return Guidance{
		Category: "Faiblesse de code (SAST)",
		What:     fmt.Sprintf("`%s` (%s) correspond à un motif à risque.", v.Title, v.RuleID),
		Why:      "L'outil signale un motif dangereux (sécurité, correctness ou bonne pratique) ; le message de l'outil précise la raison du match.",
		Fix: []string{
			"Lire le message de l'outil et la ligne concernée.",
			"Appliquer le correctif recommandé par la documentation de la règle.",
			"En cas de faux positif : passer l'issue en IGNORED avec une justification.",
		},
		References: []string{"https://semgrep.dev/r/" + v.RuleID, "https://owasp.org/www-project-top-ten/"},
	}
}

func derefStr(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

// FixContext : tout ce dont un agent (Claude Code) a besoin pour corriger
// une faille dans le dépôt local. Aucun code source n'est inclus : l'agent lit
// le fichier sur sa machine.
type FixContext struct {
	FilePath      string   `json:"file_path"`
	LineStart     int      `json:"line_start"`
	LineEnd       int      `json:"line_end"`
	Column        *int     `json:"column"`
	EndColumn     *int     `json:"end_column"`
	SeverityBlurb string   `json:"severity_blurb"`
	CWE           []string `json:"cwe"`
	Guidance      Guidance `json:"guidance"`
	AgentSteps    []string `json:"agent_steps"`
}

func BuildFixContext(v *domain.Vulnerability) FixContext {
	end := v.LineNumber
	if v.EndLine != nil {
		end = *v.EndLine
	}
	return FixContext{
		FilePath: v.FilePath, LineStart: v.LineNumber, LineEnd: end,
		Column: v.Column, EndColumn: v.EndColumn,
		SeverityBlurb: SeverityBlurb(v.Severity),
		CWE:           v.CWE,
		Guidance:      Explain(v),
		AgentSteps: []string{
			fmt.Sprintf("Ouvrir %s (chemin relatif à la racine du dépôt) autour des lignes %d-%d.", v.FilePath, v.LineNumber, end),
			"Comprendre le flux de données qui mène à la ligne signalée avant de modifier quoi que ce soit.",
			"Appliquer le correctif minimal conforme à la guidance, sans changer le comportement fonctionnel.",
			"Lancer les tests du projet concernés par le fichier.",
			fmt.Sprintf("Appeler mark_issue_fixed (ou PATCH /api/v1/issues/%d status=FIXED_PENDING_VERIFY) avec une note décrivant le correctif ; le prochain scan confirmera la résolution.", v.ID),
			"Si c'est un faux positif : ignore_issue avec une justification précise plutôt que de modifier le code.",
		},
	}
}
