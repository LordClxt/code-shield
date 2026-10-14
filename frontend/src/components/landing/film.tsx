import { useEffect, useRef } from 'react'
import { cn } from '@/lib/utils'

/**
 * Film produit muet, en boucle (sources : video-agent/landing, rendus dans
 * public/films). Lecture automatique dès le chargement, sans contrôle visible ;
 * si l'utilisateur a demandé moins d'animations, seule l'affiche s'affiche.
 */
export function Film({
  id,
  label,
  className,
  priority = false,
}: {
  /** Nom de fichier dans public/films (sans extension). */
  id: 'scan' | 'wire' | 'fix'
  /** Description de ce que montre le film, pour les lecteurs d'écran. */
  label: string
  className?: string
  /** Film au-dessus de la ligne de flottaison : on charge les données tout de suite. */
  priority?: boolean
}) {
  const ref = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    const video = ref.current
    if (!video) return
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)')
    const apply = () => {
      if (reduced.matches) video.pause()
      else void video.play().catch(() => {})
    }
    apply()
    reduced.addEventListener('change', apply)
    return () => reduced.removeEventListener('change', apply)
  }, [])

  return (
    <figure className={cn('overflow-hidden rounded-xl border border-rule-strong bg-code', className)}>
      <video
        ref={ref}
        className="block aspect-video w-full"
        muted
        loop
        playsInline
        autoPlay
        preload={priority ? 'auto' : 'metadata'}
        poster={`/films/${id}.webp`}
        aria-label={label}
      >
        <source src={`/films/${id}.webm`} type="video/webm" />
        <source src={`/films/${id}.mp4`} type="video/mp4" />
      </video>
    </figure>
  )
}
