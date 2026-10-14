// Rend les films de la landing puis les encode pour le web :
//   frontend/public/films/<id>.mp4 (H.264), <id>.webm (VP9), <id>.webp (affiche).
// Usage : npm run render [-- scan wire fix]
import { execFileSync } from "node:child_process";
import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, "../../frontend/public/films");
const tmp = join(here, "renders");
/** Affiche : l'image la plus parlante de chaque film (en secondes). */
const POSTER_AT = { scan: 6.6, wire: 5.7, fix: 10.8 };
const films = process.argv.slice(2).length ? process.argv.slice(2) : Object.keys(POSTER_AT);

mkdirSync(out, { recursive: true });
mkdirSync(tmp, { recursive: true });
const env = { ...process.env, HYPERFRAMES_NO_TELEMETRY: "1" };
const run = (cmd, args) => execFileSync(cmd, args, { stdio: "inherit", env });

for (const id of films) {
  const master = join(tmp, `${id}.mp4`);
  run("npx", ["hyperframes", "render", join(here, "films", id), "-o", master, "--fps", "60", "--quality", "delivery", "--quiet"]);
  // Web : sans piste audio, démarrage progressif, taille raisonnable.
  run("ffmpeg", ["-y", "-v", "error", "-i", master, "-an", "-c:v", "libx264", "-preset", "slow", "-crf", "23",
    "-pix_fmt", "yuv420p", "-movflags", "+faststart", join(out, `${id}.mp4`)]);
  run("ffmpeg", ["-y", "-v", "error", "-i", master, "-an", "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "36",
    "-row-mt", "1", "-deadline", "good", "-cpu-used", "2", join(out, `${id}.webm`)]);
  // ffmpeg Homebrew n'a pas libwebp : PNG intermédiaire puis cwebp.
  const png = join(tmp, `${id}-poster.png`);
  run("ffmpeg", ["-y", "-v", "error", "-ss", String(POSTER_AT[id]), "-i", master, "-frames:v", "1", "-vf", "scale=1600:-2", png]);
  run("cwebp", ["-quiet", "-q", "82", png, "-o", join(out, `${id}.webp`)]);
  console.log(`✓ ${id}`);
}
