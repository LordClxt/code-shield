# Agent Context: CodeShield Monorepo (End-to-End Specifications)

## 🎯 Mission & Objectif
CodeShield est une solution hybride d'audit de sécurité et de test d'intrusion pour le code source, centralisée dans un unique **Monorepo**. Elle couple une application **Desktop 100% native** (qui exécute les scanners localement pour garantir la confidentialité absolue du code source) et une **Plateforme Web collaborative** qui centralise les métadonnées des failles, l'historique des scans et le suivi des remédiations.

---

## 💻 1. Spécifications du Client Desktop (Kotlin Multiplatform)

L'application Desktop est un agent natif ultra-performant chargé de manipuler le système de fichiers local et d'orchestrer les scanners de sécurité.

### Pile Technique Desktop
- **Framework UI :** Compose Multiplatform (Interface 100% native et accélérée matériellement pour Windows, macOS, Linux).
- **Concurrence & Threads :** Kotlin Coroutines (`Dispatchers.IO` pour l'exécution des CLI, `Dispatchers.Main` pour le rendu de l'interface).
- **Réseau :** Ktor Client avec sérialisation JSON intégrée et authentification par Token Bearer.
- **Stockage Local :** historique JSON local (`JvmFileHistoryStore`) ; SQLDelight prévu.
- **Exécution CLI :** `java.lang.ProcessBuilder` (stdout/stderr lus en parallèle, annulation et timeout par moteur), via un registre de moteurs (`scanner/ScannerEngine.kt`) :
  - `semgrep` (SAST), `bandit` (SAST Python)
  - `gitleaks --redact` (secrets)
  - `trivy fs` (SCA + IaC + secrets), `osv-scanner` (SCA), `dependency-check.sh` (SCA)
  - `checkov` (IaC)
  - Moteurs lancés en parallèle (2 max), `cwd` = racine du projet, rapports dans un dossier temporaire (jamais dans le dépôt), auto-détection des moteurs pertinents.
  - Empreinte (`logic/Fingerprint.kt`) : sha256 salé calculé localement, seule l'empreinte part (jamais le texte de la ligne).

### Fonctionnalités Clés Desktop
- **Project Scanner :** Sélecteur de dossier local natif et configuration personnalisée des moteurs de scan.
- **Real-time Pipeline Terminal :** Console interactive asynchrone affichant les logs (STDOUT/STDERR) des outils CLI au fil de l'eau.
- **Local Code Viewer (`ui/components/CodeViewer.kt`) :** fichier entier, coloration syntaxique (`ui/code/SyntaxHighlighter.kt`), plage `line..end_line` et colonnes surlignées, marqueurs de gouttière pour toutes les failles du fichier, navigation j/k, ouverture dans l'IDE.
- **Cloud Sync Trigger :** Extraction, sérialisation et envoi du payload JSON (métadonnées uniquement) vers l'API Go, avec retries et timeout.

---

## 🌐 2. Spécifications du Front-End Web (TanStack Start SaaS)

L'interface web utilise l'architecture moderne et ultra-typée de **TanStack Start** combinée à l'écosystème React pour le suivi de sécurité.

### Pile Technique Front-End
- **Framework Full-Stack :** **TanStack Start** (React 19, TanStack Router, plugin Vite ; SSR pour la landing, `ssr: false` pour l'espace connecté). Serveur de prod : `frontend/serve.ts` (Bun).
- **Gestion des États & Cache :** TanStack Store / TanStack Query.
- **Langage :** TypeScript (Mode strict, routage entièrement typé automatiquement).
- **Styling :** Tailwind CSS ("Dark Mode First").
- **Bibliothèque de Composants :** **shadcn/ui** (Radix UI + Tailwind).
- **Visualisations :** recharts ; palette de sévérité validée (luminance, daltonisme) sur la surface sombre, toujours accompagnée d'un libellé.

### Fonctionnalités Clés Front-End (Routage Typé TanStack Start)
- **Landing & Auth Layout (`/login`, `/register`) :** Formulaires sécurisés gérés via TanStack Form et shadcn/ui.
- **Organization Dashboard (`/dashboard`) :** Cartes animées affichant la santé globale des dépôts, les scores de sécurité de l'organisation (A à F) et graphiques Sparklines de tendance.
- **Project Detail View (`/projects/$id`) :**
  - *Route Enfant `vulnerabilities` :* Tableau dynamique filtrable et triable (`@tanstack/react-table` + shadcn) par niveau de sévérité.
  - *Route Enfant `metrics` :* Graphiques d'évolution temporelle des failles (nouvelles vs corrigées).
- **Settings & API Keys (`/settings/keys`) :** Clés à scopes (`sync:write`, `issues:read`, `issues:write`), restriction par projet, commande `claude mcp add` pré-remplie.

---

## 🐹 3. Spécifications du Backend (Go — `/backend-go/`)

Le backend est le point d'ancrage de la donnée. Il stocke les résultats, calcule les métriques, orchestre l'authentification et expose CodeShield aux agents IA (MCP). Il a remplacé le backend FastAPI historique (supprimé).

### Pile Technique Backend
- **Langage :** Go, routeur `chi`, logs `slog`, arrêt propre.
- **Base de Données :** PostgreSQL via `pgx/v5` ; migrations `goose` embarquées (`migrations/`), appliquées au démarrage.
- **Sécurité :** JWT HS256 (secret obligatoire hors dev), `bcrypt`, clés API `csk_` stockées hashées (sha256) avec scopes et restriction de projet, rate limiting, en-têtes de sécurité.
- **Validation :** décodage strict (`DisallowUnknownFields`) du payload de synchro : tout champ de code → 422.
- **Contrat :** `contracts/openapi.yaml` (source de vérité, servi sur `/openapi.json`) + fixtures partagées `contracts/fixtures/` testées en Go, Kotlin et TypeScript.

### Structure en Couches (`/backend-go/internal/`)
- `api/v1/` : **Contrôleurs** HTTP + middleware d'authentification et de scopes.
- `service/` : **Logique Métier** (synchro différentielle, score A–F, remédiation/`fix_context`, transitions de statut).
- `repository/` : **Accès Données** (tout le SQL).
- `domain/` : **Entités, enums et validation** du contrat.
- `mcp/` : **Serveur MCP** (Streamable HTTP sur `/mcp`) : `list_projects`, `list_issues`, `get_issue`, `set_issue_status`, `mark_issue_fixed`, `ignore_issue`, `get_project_score`, ressource `codeshield://issue/{id}`, prompt `fix_issues`.

### Algorithme du Moteur de Synchronisation (`/api/v1/sync/submit`)
Transaction unique, projet verrouillé :
1. Identité = `fingerprint` si fourni, sinon (outil, règle, fichier, ligne).
2. Match sur faille active → `last_seen` et position mis à jour (le code a pu bouger).
3. Match sur `RESOLVED` ou `FIXED_PENDING_VERIFY` → `REOPENED`.
4. Match sur `IGNORED` → reste ignorée (hors score).
5. Faille active absente du scan → `RESOLVED`.
6. Nouvelle faille → `OPEN`.

Statuts : `OPEN | IN_PROGRESS | FIXED_PENDING_VERIFY | RESOLVED | IGNORED | REOPENED`. Un agent (clé API) ne peut jamais poser `RESOLVED` : seul le scan suivant (ou un humain connecté) confirme une correction.

---

## 📝 Directives Globales du Monorepo (Guidelines)
- **Sécurité Intellectuelle :** Interdiction absolue de faire transiter ou de stocker du code source brut sur le réseau ou sur la base de données du serveur. Seules les métadonnées de positionnement (fichiers, lignes, règles violées) sont transmises.
- **Alignement des Types :** Les Enums de sévérité (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`) et les structures JSON doivent correspondre rigoureusement entre le contrat `contracts/openapi.yaml`, le domaine Go du backend, les classes de sérialisation Kotlin (`kotlinx.serialization`) du desktop, et les types TypeScript générés du front-end TanStack. Toute évolution passe d'abord par le contrat et les fixtures partagées.
