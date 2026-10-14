/**
 * Serveur de production minimal (Bun) : fichiers statiques de dist/client,
 * puis le handler TanStack Start (SSR) pour tout le reste.
 *   bun run build && bun run start
 */
import { resolve, sep } from 'node:path'
import handler from './dist/server/server.js'

const root = resolve(import.meta.dir, 'dist/client')
const port = Number(process.env.PORT ?? 3000)
// Installeurs de l'app desktop (hors build : déposés par desktop/scripts/package-installer.sh ou montés en volume).
const downloadsDir = resolve(process.env.DOWNLOADS_DIR ?? resolve(import.meta.dir, 'downloads'))
// Installeur absent en local : redirection vers la dernière release GitHub (publiée par la CI sur tag vX.Y.Z).
const releasesUrl = (process.env.DOWNLOADS_FALLBACK_URL ?? 'https://github.com/LordClxt/code-shield/releases/latest/download').replace(/\/$/, '')
const INSTALLER = /^\/downloads\/(CodeShield-(?:macos\.dmg|windows\.msi|linux\.deb))$/

/** Installeur demandé : liste fermée de noms, servi en pièce jointe (sinon redirigé vers la release). */
async function installer(pathname: string): Promise<Response | null> {
  const name = INSTALLER.exec(pathname)?.[1]
  if (!name) return null
  const file = Bun.file(resolve(downloadsDir, name))
  if (!(await file.exists())) return Response.redirect(`${releasesUrl}/${name}`, 302)
  return new Response(file, {
    headers: {
      'Content-Type': 'application/octet-stream',
      'Content-Disposition': `attachment; filename="${name}"`,
      'Cache-Control': 'public, max-age=300',
    },
  })
}

async function staticFile(pathname: string): Promise<Response | null> {
  if (pathname === '/') return null
  let decoded: string
  try {
    decoded = decodeURIComponent(pathname)
  } catch {
    return null
  }
  const path = resolve(root, `.${decoded}`)
  // Refuse toute sortie de dist/client (../, encodages).
  if (!path.startsWith(root + sep)) return null
  const file = Bun.file(path)
  if (!(await file.exists())) return null
  const immutable = decoded.startsWith('/assets/')
  return new Response(file, {
    headers: immutable ? { 'Cache-Control': 'public, max-age=31536000, immutable' } : { 'Cache-Control': 'public, max-age=300' },
  })
}

Bun.serve({
  port,
  async fetch(req) {
    const url = new URL(req.url)
    if (req.method === 'GET' || req.method === 'HEAD') {
      const res = (await installer(url.pathname)) ?? (await staticFile(url.pathname))
      if (res) return res
    }
    const res = await handler.fetch(req)
    res.headers.set('X-Content-Type-Options', 'nosniff')
    res.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin')
    res.headers.set('X-Frame-Options', 'DENY')
    return res
  },
})

console.log(`CodeShield web sur http://localhost:${port}`)
