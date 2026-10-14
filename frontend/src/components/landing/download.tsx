import { Download, Laptop, Monitor, SquareTerminal } from 'lucide-react'
import { motion } from 'motion/react'
import { useEffect, useState, type ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { Card } from './sections'
import { inView, rise, stagger } from './motion'

/**
 * Téléchargement de l'app desktop. Les installeurs sont servis par `serve.ts`
 * (dossier DOWNLOADS_DIR) ou par un stockage externe via VITE_DOWNLOAD_URL.
 */
export const DOWNLOAD_URL: string = (import.meta.env.VITE_DOWNLOAD_URL ?? '/downloads').replace(/\/$/, '')

export type Os = 'macos' | 'windows' | 'linux'

export const PLATFORMS: { os: Os; label: string; format: string; file: string; requirement: string; icon: ReactNode }[] = [
  { os: 'macos', label: 'macOS', format: '.dmg', file: 'CodeShield-macos.dmg', requirement: 'Installeur .dmg', icon: <Laptop className="size-5" /> },
  { os: 'windows', label: 'Windows', format: '.msi', file: 'CodeShield-windows.msi', requirement: 'Installeur .msi', icon: <Monitor className="size-5" /> },
  { os: 'linux', label: 'Linux', format: '.deb', file: 'CodeShield-linux.deb', requirement: 'Paquet .deb (Debian, Ubuntu)', icon: <SquareTerminal className="size-5" /> },
]

export const downloadHref = (file: string) => `${DOWNLOAD_URL}/${file}`

/** Système du visiteur, d'après le navigateur. Pure -> testée. */
export function detectOs(userAgent: string, platform = ''): Os | null {
  const s = `${platform} ${userAgent}`.toLowerCase()
  if (/iphone|ipad|android/.test(s)) return null
  if (/mac/.test(s)) return 'macos'
  if (/win/.test(s)) return 'windows'
  if (/linux|x11/.test(s)) return 'linux'
  return null
}

/** Détection après hydratation : le rendu serveur reste identique pour tous. */
function useDetectedOs(): Os | null {
  const [os, setOs] = useState<Os | null>(null)
  useEffect(() => {
    setOs(detectOs(navigator.userAgent, navigator.platform))
  }, [])
  return os
}

type Availability = 'unknown' | 'ready' | 'missing'

/**
 * Vérifie qu'un installeur est publié (HEAD). Seul un 404 explicite le marque absent :
 * une erreur réseau ou CORS (stockage externe) laisse le lien actif.
 */
function useAvailability(): Record<Os, Availability> {
  const [state, setState] = useState<Record<Os, Availability>>({ macos: 'unknown', windows: 'unknown', linux: 'unknown' })
  useEffect(() => {
    let alive = true
    for (const p of PLATFORMS) {
      fetch(downloadHref(p.file), { method: 'HEAD' })
        .then((res) => {
          if (!alive) return
          const next: Availability = res.ok ? 'ready' : res.status === 404 ? 'missing' : 'unknown'
          setState((s) => ({ ...s, [p.os]: next }))
        })
        .catch(() => {})
    }
    return () => {
      alive = false
    }
  }, [])
  return state
}

/** Bouton principal : l'installeur du système détecté, sinon un lien vers les trois. */
export function DownloadButton({ className }: { className?: string }) {
  const os = useDetectedOs()
  const platform = PLATFORMS.find((p) => p.os === os)
  if (!platform) {
    return (
      <a href="#telecharger" className={cn('lp-btn gap-2', className)}>
        <Download className="size-4" aria-hidden />
        Télécharger l’app
      </a>
    )
  }
  return (
    <a href={downloadHref(platform.file)} download className={cn('lp-btn gap-2', className)}>
      <Download className="size-4" aria-hidden />
      Télécharger pour {platform.label}
    </a>
  )
}

/** Les trois installeurs, celui du visiteur mis en avant. */
export function DownloadCards() {
  const os = useDetectedOs()
  const availability = useAvailability()
  return (
    <motion.div className="grid gap-4 md:grid-cols-3" variants={stagger(0.12)} {...inView(0.2)}>
      {PLATFORMS.map((p) => {
        const current = p.os === os
        const missing = availability[p.os] === 'missing'
        return (
          <motion.div key={p.os} variants={rise} className="min-w-0">
            <Card className="grid h-full content-start gap-5 p-7" hot={current} glow={current ? 0.45 : 0.15} glowAt="50% 0%">
              <div className="flex items-center gap-3">
                <span className="lp-orb size-11 text-white">{p.icon}</span>
                <div className="grid">
                  <p className="text-[16px] font-medium text-white">{p.label}</p>
                  <p className="text-[13px] text-fog">{p.requirement}</p>
                </div>
                {current ? <span className="lp-pill ml-auto text-[12px]">Votre système</span> : null}
              </div>
              {missing ? (
                <span className="lp-btn-ghost lp-sm cursor-not-allowed opacity-60" aria-disabled>
                  Bientôt disponible
                </span>
              ) : (
                <a href={downloadHref(p.file)} download className={cn(current ? 'lp-btn' : 'lp-btn-ghost', 'lp-sm gap-2')}>
                  <Download className="size-4" aria-hidden />
                  Télécharger {p.format}
                </a>
              )}
            </Card>
          </motion.div>
        )
      })}
    </motion.div>
  )
}

/** Les trois étapes après le téléchargement. */
export function InstallSteps() {
  const steps: [string, ReactNode][] = [
    ['Installez l’app', 'Ouvrez l’installeur. Au premier lancement, l’app propose d’installer les scanners manquants.'],
    ['Scannez un dossier', 'Choisissez votre projet : les moteurs utiles tournent en local, deux à la fois.'],
    [
      'Corrigez avec Claude Code',
      <>
        Avec{' '}
        <a href="https://claude.com/claude-code" className="text-paper underline-offset-4 hover:underline" target="_blank" rel="noreferrer">
          Claude Code
        </a>{' '}
        installé, un clic sur une faille (ou sur toutes les critiques) le lance dans votre projet.
      </>,
    ],
  ]
  return (
    <motion.ol className="grid gap-4 md:grid-cols-3" variants={stagger(0.1)} {...inView(0.3)}>
      {steps.map(([title, body], i) => (
        <motion.li key={title} variants={rise} className="grid content-start gap-2 rounded-2xl bg-[#0d0a16] p-5 ring-1 ring-[rgb(167_139_250/0.16)]">
          <span className="font-mono text-[12.5px] text-[#a78bfa]">0{i + 1}</span>
          <p className="text-[15px] font-medium text-white">{title}</p>
          <p className="text-[14px] leading-relaxed text-fog">{body}</p>
        </motion.li>
      ))}
    </motion.ol>
  )
}
