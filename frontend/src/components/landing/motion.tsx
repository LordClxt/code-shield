import { Link } from '@tanstack/react-router'
import {
  AnimatePresence,
  animate,
  motion,
  useInView,
  useMotionValue,
  useMotionValueEvent,
  useReducedMotion,
  useScroll,
  useSpring,
  useTransform,
  type Variants,
} from 'motion/react'
import { useEffect, useRef, useState, type ComponentProps, type ReactNode, type RefObject } from 'react'
import { Logo } from '@/components/domain'
import { cn } from '@/lib/utils'

/*
 * Mouvement de la landing. Une seule courbe (sortie douce, sans rebond), des
 * entrées déclenchées une fois à l'entrée dans l'écran, et des effets liés au
 * scroll désactivés quand l'utilisateur demande moins d'animations
 * (`MotionConfig reducedMotion="user"` couvre les variants, `useReducedMotion`
 * le reste).
 */

export const EASE = [0.16, 1, 0.3, 1] as const

/** Montée + défloutage : titres, paragraphes, boutons. */
export const rise: Variants = {
  hidden: { opacity: 0, y: 24, filter: 'blur(10px)' },
  show: { opacity: 1, y: 0, filter: 'blur(0px)', transition: { duration: 0.9, ease: EASE } },
}

/** Cartes : montée et léger zoom, puis leurs illustrations s'animent en cascade. */
export const cardIn: Variants = {
  hidden: { opacity: 0, y: 40, scale: 0.97 },
  show: {
    opacity: 1,
    y: 0,
    scale: 1,
    transition: { duration: 0.9, ease: EASE, staggerChildren: 0.08, delayChildren: 0.2 },
  },
}

/** Petits éléments (pastilles, puces) : apparition à ressort. */
export const pop: Variants = {
  hidden: { opacity: 0, scale: 0.6, y: 8 },
  show: { opacity: 1, scale: 1, y: 0, transition: { type: 'spring', stiffness: 380, damping: 22 } },
}

/** Lignes d'une liste ou d'un diff : glissement latéral. */
export const slide: Variants = {
  hidden: { opacity: 0, x: -14 },
  show: { opacity: 1, x: 0, transition: { duration: 0.6, ease: EASE } },
}

export function stagger(each = 0.08, delay = 0): Variants {
  return { hidden: {}, show: { transition: { staggerChildren: each, delayChildren: delay } } }
}

/** Déclenche les variants `hidden` → `show` une fois, à l'entrée dans l'écran. */
export function inView(amount = 0.25) {
  return { initial: 'hidden', whileInView: 'show', viewport: { once: true, amount } } as const
}

type DivProps = ComponentProps<typeof motion.div>

/** Un bloc qui apparaît seul (par défaut : montée + défloutage). */
export function Reveal({ variants = rise, amount = 0.3, ...props }: DivProps & { amount?: number }) {
  return <motion.div variants={variants} {...inView(amount)} {...props} />
}

/** Un conteneur dont les enfants à variants apparaissent l'un après l'autre. */
export function Stagger({ each = 0.1, delay = 0, amount = 0.2, ...props }: DivProps & { each?: number; delay?: number; amount?: number }) {
  return <motion.div variants={stagger(each, delay)} {...inView(amount)} {...props} />
}

/* ------------------------------------------------------------------------- */
/* Effets liés au scroll                                                      */
/* ------------------------------------------------------------------------- */

/** Fine barre de progression de lecture, en haut de la fenêtre. */
export function ScrollProgress() {
  const { scrollYProgress } = useScroll()
  const scaleX = useSpring(scrollYProgress, { stiffness: 140, damping: 30, restDelta: 0.001 })
  return (
    <motion.div
      aria-hidden
      className="fixed inset-x-0 top-0 z-50 h-[2px] origin-left bg-gradient-to-r from-[#6d28d9] via-[#a78bfa] to-[#f5f3ff] shadow-[0_0_12px_#8b5cf6]"
      style={{ scaleX }}
    />
  )
}

/**
 * 1 quand l'utilisateur demande moins d'animations. C'est une MotionValue : les
 * effets liés au scroll restent branchés et reviennent à leur position neutre
 * (retirer le style après coup laisserait la transformation déjà posée, y compris
 * celle rendue côté serveur).
 */
function useStill() {
  const reduce = useReducedMotion()
  const still = useMotionValue(reduce ? 1 : 0)
  useEffect(() => still.set(reduce ? 1 : 0), [reduce, still])
  return still
}

/** Parallaxe du hero : le texte s'efface en descendant, l'horizon s'élargit. */
export function useHeroParallax(ref: RefObject<HTMLElement | null>) {
  const still = useStill()
  const { scrollYProgress: p } = useScroll({ target: ref, offset: ['start start', 'end start'] })
  const y = useTransform(() => (still.get() ? 0 : p.get() * 180))
  const opacity = useTransform(() => (still.get() ? 1 : Math.max(0, 1 - p.get() * 2)))
  const scale = useTransform(() => (still.get() ? 1 : 1 + p.get() * 0.4))
  const dotsY = useTransform(() => (still.get() ? 0 : p.get() * 100))
  return { content: { y, opacity }, horizon: { scale }, dots: { y: dotsY } }
}

/** Le film se redresse en montant dans l'écran, comme un écran posé qu'on relève. */
export function TiltIn({ children, className, id }: { children: ReactNode; className?: string; id?: string }) {
  const ref = useRef<HTMLDivElement>(null)
  const still = useStill()
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start end', 'start 0.2'] })
  const p = useSpring(scrollYProgress, { stiffness: 110, damping: 26, restDelta: 0.001 })
  const rotateX = useTransform(() => (still.get() ? 0 : (1 - p.get()) * 26))
  const scale = useTransform(() => (still.get() ? 1 : 0.88 + p.get() * 0.12))
  return (
    <motion.div ref={ref} id={id} className={className} style={{ rotateX, scale, transformPerspective: 1400, transformOrigin: '50% 0%' }}>
      {children}
    </motion.div>
  )
}

/** Compteur qui monte jusqu'à sa valeur à l'entrée dans l'écran. */
export function CountUp({ to, className }: { to: number; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null)
  const seen = useInView(ref, { once: true, amount: 0.8 })
  const reduce = useReducedMotion()
  useEffect(() => {
    const el = ref.current
    if (!el || !seen) return
    if (reduce) {
      el.textContent = String(to)
      return
    }
    const controls = animate(0, to, {
      duration: 1.6,
      delay: 0.5,
      ease: EASE,
      onUpdate: (v) => {
        el.textContent = String(Math.round(v))
      },
    })
    return () => controls.stop()
  }, [seen, reduce, to])
  return (
    <span ref={ref} className={className}>
      0
    </span>
  )
}

/* ------------------------------------------------------------------------- */
/* Navigation flottante                                                       */
/* ------------------------------------------------------------------------- */

/**
 * Barre de navigation en verre qui descend une fois le hero quitté, avec la
 * section courante surlignée (la pastille glisse d'un lien à l'autre).
 */
export function FloatingNav({ items }: { items: readonly { href: string; label: string }[] }) {
  const { scrollY } = useScroll()
  const [shown, setShown] = useState(false)
  const [active, setActive] = useState<string | null>(null)

  useMotionValueEvent(scrollY, 'change', (y) => setShown(y > 560))

  useEffect(() => {
    setShown(window.scrollY > 560)
    // Les ancres pointent sur les titres : on observe la section qui les contient.
    const hrefBySection = new Map<Element, string>()
    for (const i of items) {
      const el = document.querySelector(i.href)
      if (el) hrefBySection.set(el.closest('section') ?? el, i.href)
    }
    const observer = new IntersectionObserver(
      (entries) => {
        for (const e of entries) if (e.isIntersecting) setActive(hrefBySection.get(e.target) ?? null)
      },
      { rootMargin: '-40% 0px -55% 0px' },
    )
    hrefBySection.forEach((_, s) => observer.observe(s))
    return () => observer.disconnect()
  }, [items])

  return (
    <AnimatePresence>
      {shown && (
        <motion.header
          key="nav"
          initial={{ y: -80, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -80, opacity: 0 }}
          transition={{ type: 'spring', stiffness: 320, damping: 30 }}
          className="pointer-events-none fixed inset-x-0 top-3 z-40 flex justify-center px-3"
        >
          <div className="pointer-events-auto flex h-12 items-center gap-2 rounded-full bg-[#0b0912]/75 pr-1.5 pl-4 shadow-[0_12px_40px_-12px_rgb(0_0_0/0.8)] ring-1 ring-[rgb(167_139_250/0.2)] backdrop-blur-xl">
            <Link to="/" aria-label="CodeShield, accueil">
              <Logo />
            </Link>
            <nav className="mx-2 hidden items-center lg:flex" aria-label="Sections de la page">
              {items.map((n) => (
                <a
                  key={n.href}
                  href={n.href}
                  className={cn(
                    'relative isolate rounded-full px-3 py-1.5 text-[13.5px] transition-colors',
                    active === n.href ? 'text-white' : 'text-fog hover:text-paper',
                  )}
                >
                  {active === n.href && (
                    <motion.span
                      layoutId="nav-active"
                      className="absolute inset-0 -z-10 rounded-full bg-[rgb(139_92_246/0.22)] ring-1 ring-[rgb(196_181_253/0.35)]"
                      transition={{ type: 'spring', stiffness: 380, damping: 32 }}
                    />
                  )}
                  {n.label}
                </a>
              ))}
            </nav>
            <Link to="/register" className="lp-btn lp-sm">
              Créer un compte
            </Link>
          </div>
        </motion.header>
      )}
    </AnimatePresence>
  )
}
