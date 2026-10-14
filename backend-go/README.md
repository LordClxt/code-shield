# CodeShield API (Go)

Backend CodeShield v2 : REST `/api/v1` + serveur MCP `/mcp` pour les agents IA.
Il a remplacé le backend FastAPI historique, avec les mêmes routes et les mêmes réponses JSON.

**Règle absolue :** aucun code source n'est reçu ni stocké. Un finding ne porte que des métadonnées (fichier, lignes, règle, CWE) et une empreinte sha256. Tout champ hors contrat (`snippet`, `source_code`…) est rejeté en 422.

## Démarrer

```bash
cp .env.example .env              # puis renseigner JWT_SECRET_KEY hors dev
make run                          # nécessite un PostgreSQL (DATABASE_URL)
# ou tout-en-un :
JWT_SECRET_KEY=$(openssl rand -base64 48) docker compose up --build
```

Les migrations ([goose](https://github.com/pressly/goose), `migrations/`) s'appliquent au démarrage.

## Tests

```bash
make test   # PostgreSQL embarqué téléchargé au 1er lancement ; ou TEST_DATABASE_URL=postgres://…
```

`test/` couvre :
- la parité avec l'ancienne suite pytest ;
- le moteur différentiel v2 (empreintes, réouverture, IGNORED, FIXED_PENDING_VERIFY) ;
- l'API `/issues` (scopes, pagination, isolation) ;
- le workflow MCP complet ;
- le garde-fou anti code source.

## Architecture

| Dossier | Rôle |
|---|---|
| `cmd/api` | Point d'entrée (arrêt propre) |
| `internal/api` | Routeur racine : santé, `/openapi.json`, CORS, en-têtes de sécurité, rate limiting |
| `internal/api/v1` | Contrôleurs HTTP et middleware d'authentification / scopes |
| `internal/service` | Logique métier : synchro différentielle, score A–F, remédiation, transitions de statut, sécurité |
| `internal/repository` | Tout le SQL (pgx) |
| `internal/domain` | Entités, enums et validation du contrat |
| `internal/mcp` | Serveur MCP (outils, ressource, prompt) |
| `migrations` | Schéma PostgreSQL |

Le contrat se trouve dans `../contracts/openapi.yaml`. `make contracts` le recopie dans le binaire, et un test vérifie que les deux copies sont identiques.

## Brancher Claude Code (MCP)

1. Dans le web (Paramètres → Clés API), créez une clé avec les scopes `issues:read` et `issues:write`. Vous pouvez la restreindre à un projet.
2. Lancez :

```bash
claude mcp add --transport http codeshield https://<votre-api>/mcp \
  --header "Authorization: Bearer csk_..."
```

3. Dans Claude Code, demandez par exemple : « corrige les issues CRITICAL du projet 3 ». Vous pouvez aussi utiliser le prompt MCP `fix_issues`.

**Outils disponibles :**

| Outil | Effet |
|---|---|
| `list_projects` | Liste des projets |
| `list_issues` | Liste des issues, les plus graves d'abord |
| `get_issue` | Détail d'une issue, avec `fix_context` (fichier, lignes, CWE, guidance, étapes) |
| `set_issue_status` | Passe l'issue en `IN_PROGRESS` ou `OPEN` |
| `mark_issue_fixed` | Passe l'issue en `FIXED_PENDING_VERIFY` |
| `ignore_issue` | Ignore l'issue (justification obligatoire) |
| `get_project_score` | Score du projet |

**Cycle de vie d'une issue :** l'agent corrige le code en local, puis appelle `mark_issue_fixed`. Au scan desktop suivant :
- si la faille a disparu, elle passe en `RESOLVED` ;
- si elle est toujours là, elle passe en `REOPENED`.

Un agent ne peut jamais poser `RESOLVED` lui-même. Ce statut est réservé à un humain connecté (session JWT) ou au scan.

## Scopes des clés API

| Scope | Autorise |
|---|---|
| `sync:write` | `POST /api/v1/sync/submit` (desktop). C'est le scope par défaut. |
| `issues:read` | `GET /api/v1/issues*` et la connexion au MCP |
| `issues:write` | `PATCH /api/v1/issues/{id}`, `POST /api/v1/issues/bulk` et les outils MCP d'écriture |

Une clé ne peut jamais gérer le compte : routes `/projects`, `/keys` et `/auth/me`.
