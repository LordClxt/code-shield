# Films de la landing

Trois films produit muets, en boucle, intégrés à la landing par `frontend/src/components/landing/film.tsx`.

| Film | Section de la landing | Ce qu'il montre |
|---|---|---|
| `films/scan` (10,5 s) | Hero | Le scan local : les moteurs tournent deux à la fois, les repères tombent sur les lignes 4 et 10, puis la caméra pousse sur l'injection SQL |
| `films/wire` (10,5 s) | Ce qui part, ce qui reste | Deux fiches de métadonnées passent le contrat d'API ; un envoi avec un `snippet` est refusé en 422 |
| `films/fix` (12 s) | Claude Code | Claude Code corrige via MCP, puis le scan suivant confirme « Résolue » |

Chaque film est une composition [HyperFrames](https://github.com/heygen-com/hyperframes) : du HTML et une timeline GSAP en pause. Il réutilise les tokens et les polices du site (`assets/base.css`). GSAP et les polices sont en local, si bien que le rendu ne dépend pas du réseau.

## Commandes

```bash
npm install
npm run preview                       # studio d'aperçu
npm run lint
npm run render                        # les trois films
npm run render -- fix                 # un seul film
```

`npm run render` produit, dans `frontend/public/films/` :
- `<id>.mp4` (H.264) ;
- `<id>.webm` (VP9) ;
- `<id>.webp`, l'affiche du film.

Le rendu se fait en 1920x1080 et 60 i/s. L'image de l'affiche est choisie dans `POSTER_AT`, dans `render.mjs`.

Au premier lancement, `npx hyperframes browser ensure` télécharge le Chrome de rendu. La télémétrie est coupée (`HYPERFRAMES_NO_TELEMETRY=1`).

## Règles de composition

Ces règles viennent du skill `hyperframes-animation` :
- une seule timeline GSAP en pause, enregistrée sur `window.__timelines["<id>"]` ;
- des `fromTo` avec des valeurs absolues, sans `Math.random` ni `Date.now`, pour pouvoir caler le film sur n'importe quelle image ;
- seulement des transformations, de l'opacité et des couleurs, jamais de `width`, `height`, `top` ou `left` animés ;
- un fondu depuis et vers le fond `--night`, pour que la boucle se raccorde sans saut.
