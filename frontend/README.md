# CodeShield Web v2 (TanStack Start)

Application web CodeShield : landing (SSR), espace connecté (rendu client), suivi des failles, clés API et branchement de Claude Code via MCP.

## Pile

- **Framework :** TanStack Start, avec le routage par fichiers de `src/routes`. `routeTree.gen.ts` est généré.
  - La landing et les pages d'accès sont rendues côté serveur (SSR).
  - L'espace `/_app` est en `ssr: false`, car le jeton est stocké en `localStorage`.
- **Données :** TanStack Query pour les loaders (`ensureQueryData`) et les mutations (`invalidateQueries`), TanStack Table pour la liste des failles.
- **UI :** Tailwind CSS v4 (tokens dans `src/styles.css`) et composants façon shadcn/ui (Radix, cva, `cn()`) dans `src/components/ui`.
- **Graphiques :** recharts.
- **Typographie :** Bricolage Grotesque (titres), IBM Plex Sans (texte), IBM Plex Mono (code).

## Commandes (Bun)

```bash
bun install
bun run dev        # http://localhost:3000
bun run test       # vitest : contrat partagé, score, garde-fou anti-fuite
bun run typecheck  # tsc --noEmit
bun run build      # build client + serveur (dist/)
bun run start      # serveur de production (serve.ts)
bun run contracts  # régénère src/lib/api-schema.gen.ts depuis ../contracts/openapi.yaml
```

L'API se configure avec `VITE_API_URL` (défaut `http://localhost:8000`), qui est intégrée au build.

Docker :

```bash
docker build --build-arg VITE_API_URL=https://api.example.com -t codeshield-web .
```

## Alignement des types

Les types de `src/lib/types.ts` dérivent de `contracts/openapi.yaml`, le même contrat que le backend Go et le desktop Kotlin.

`src/lib/__tests__/contract.test.ts` valide la fixture partagée `contracts/fixtures/sync_request.json`, qui est aussi testée côté Go et côté Kotlin.

`api.ts` refuse tout payload contenant `source_code`, `snippet` ou `code` (Sécurité Intellectuelle).

## Couleurs de sévérité

La palette (`SEVERITY_META`) et la paire « nouvelles / corrigées » ont été validées sur la surface `#141D2E` : bande de luminance, séparation pour les daltoniens, contraste. Une couleur ne s'affiche jamais sans son libellé.
