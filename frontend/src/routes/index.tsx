import { createFileRoute, Link } from '@tanstack/react-router'
import { MotionConfig, motion, type MotionStyle } from 'motion/react'
import { useRef, type PointerEvent, type ReactNode } from 'react'
import { GITHUB_URL, GithubIcon } from '@/components/brand'
import { Logo } from '@/components/domain'
import { Film } from '@/components/landing/film'
import {
  FloatingNav,
  Reveal,
  ScrollProgress,
  Stagger,
  TiltIn,
  cardIn,
  pop,
  rise,
  stagger,
  useHeroParallax,
} from '@/components/landing/motion'
import {
  Card,
  CategoryCards,
  EngineStrip,
  Faq,
  PayloadTrio,
  ProcessCards,
  SectionTitle,
  ShieldMark,
  WhyBento,
} from '@/components/landing/sections'
import { DownloadButton, DownloadCards, InstallSteps } from '@/components/landing/download'

export const Route = createFileRoute('/')({
  component: Landing,
})

const NAV = [
  { href: '#pourquoi', label: 'Pourquoi CodeShield' },
  { href: '#fonctionnement', label: 'Fonctionnement' },
  { href: '#confidentialite', label: 'Confidentialité' },
  { href: '#moteurs', label: 'Moteurs' },
  { href: '#telecharger', label: 'Télécharger' },
  { href: '#questions', label: 'Questions' },
]

/** Horizon lumineux : la signature de la page (hero, pied de page). */
function Horizon({ side = 'bottom', className, style }: { side?: 'top' | 'bottom'; className?: string; style?: MotionStyle }) {
  const at = side === 'bottom' ? '50% 100%' : '50% 0%'
  return (
    <motion.div
      aria-hidden
      className={`pointer-events-none absolute inset-x-0 ${className ?? (side === 'bottom' ? 'bottom-0' : 'top-0')}`}
      style={{
        background: `radial-gradient(58% 100% at ${at}, #f5f3ff 0%, #c4b5fd 9%, #8b5cf6 24%, rgb(109 40 217 / 0.6) 40%, rgb(76 29 149 / 0.25) 60%, transparent 80%)`,
        transformOrigin: at,
        ...style,
      }}
    />
  )
}

/** Film dans un écrin lumineux. */
function FilmFrame({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={`lp-card lp-card-hot p-1.5 ${className ?? ''}`}>{children}</div>
}

/** Les variants respectent « réduire les animations » du système (transformations coupées, fondus gardés). */
function Landing() {
  return (
    <MotionConfig reducedMotion="user">
      <LandingPage />
    </MotionConfig>
  )
}

function LandingPage() {
  const hero = useRef<HTMLElement>(null)
  const dots = useRef<HTMLDivElement>(null)
  const parallax = useHeroParallax(hero)

  // La trame s'illumine autour du pointeur (variables CSS, sans re-rendu).
  const lightDots = (e: PointerEvent) => {
    const el = dots.current
    if (!el) return
    const r = el.getBoundingClientRect()
    el.style.setProperty('--hx', `${e.clientX - r.left}px`)
    el.style.setProperty('--hy', `${e.clientY - r.top}px`)
  }

  return (
    <div className="min-h-dvh bg-night p-2 sm:p-3">
      <ScrollProgress />
      <FloatingNav items={NAV} />

      {/* Hero : un panneau sombre, une trame de points, un horizon violet. */}
      <section
        ref={hero}
        onPointerMove={lightDots}
        className="relative overflow-hidden rounded-[28px] bg-[#0b0912] ring-1 ring-[rgb(167_139_250/0.12)]"
        aria-labelledby="titre"
      >
        <motion.div ref={dots} aria-hidden className="absolute inset-x-0 top-0 h-[70%]" style={parallax.dots}>
          <div className="lp-dots absolute inset-0 [mask-image:linear-gradient(to_bottom,black,transparent)]" />
          <div className="lp-dots-hot absolute inset-0" />
        </motion.div>
        {/* Le cœur de l'horizon se pose sur le bord haut du film, qui chevauche le panneau. */}
        <Horizon className="lp-horizon-in bottom-40 h-72 sm:bottom-72 sm:h-96" style={parallax.horizon} />
        <div aria-hidden className="absolute inset-x-0 bottom-0 h-40 bg-[#0b0912] sm:h-72" />

        <header className="relative mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <Link to="/" aria-label="CodeShield, accueil">
            <Logo />
          </Link>
          <nav className="ml-4 hidden items-center gap-1 text-[14px] text-fog xl:flex" aria-label="Navigation principale">
            {NAV.map((n) => (
              <a key={n.href} href={n.href} className="rounded-full px-3 py-1.5 whitespace-nowrap transition-colors hover:bg-white/5 hover:text-paper">
                {n.label}
              </a>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <a href={GITHUB_URL} target="_blank" rel="noreferrer" className="lp-btn-ghost lp-sm gap-2" aria-label="Code source sur GitHub">
              <GithubIcon className="size-4" />
              <span className="hidden sm:inline">GitHub</span>
            </a>
            <a href="#telecharger" className="lp-btn lp-sm">
              Télécharger
            </a>
          </div>
        </header>

        {/* Entrée en cascade au chargement ; en scrollant, le texte descend et s'efface. */}
        <motion.div style={parallax.content} className="relative">
          <motion.div
            className="mx-auto grid max-w-4xl justify-items-center gap-7 px-4 pt-20 pb-[18rem] text-center sm:px-6 sm:pt-28 sm:pb-[25rem]"
            variants={stagger(0.12, 0.15)}
            initial="hidden"
            animate="show"
          >
            <motion.a href="#claude-code" className="lp-pill lp-pill-live hover:text-paper" variants={pop}>
              <span className="lp-ping relative size-1.5 rounded-full bg-[#a78bfa] shadow-[0_0_8px_#a78bfa]" aria-hidden />
              Claude Code corrige vos failles depuis l’app
            </motion.a>
            <motion.h1
              id="titre"
              className="text-[clamp(2.8rem,7vw,5.4rem)] leading-[1.02] font-medium tracking-[-0.045em]"
              variants={stagger(0.14)}
            >
              {/* Dégradé par ligne : background-clip ne traverse pas les enfants transformés. */}
              <motion.span className="lp-text-fade block" variants={rise}>
                Trouvez les failles.
              </motion.span>
              <motion.span className="lp-text-fade block" variants={rise}>
                Gardez votre code.
              </motion.span>
            </motion.h1>
            <motion.p className="max-w-xl text-[17px] leading-relaxed text-fog" variants={rise}>
              L’app desktop scanne votre dépôt sur votre machine, ne partage que l’emplacement des failles et laisse Claude
              Code les corriger.
            </motion.p>
            <motion.div className="flex flex-wrap justify-center gap-3" variants={rise}>
              <DownloadButton />
              <a href="#film" className="lp-btn-ghost">
                Voir le scan
              </a>
            </motion.div>
            <motion.div className="mt-8 grid justify-items-center gap-4" variants={stagger(0.08)}>
              <motion.p className="text-[13.5px] text-fog" variants={rise}>
                Propulsé par sept scanners open source
              </motion.p>
              <EngineStrip />
            </motion.div>
          </motion.div>
        </motion.div>
      </section>

      <main id="contenu" className="mx-auto max-w-6xl px-2 sm:px-4">
        {/* Le film du scan chevauche l'horizon. */}
        <TiltIn id="film" className="relative z-10 -mt-40 scroll-mt-8 sm:-mt-72">
          <FilmFrame>
            <Film
              id="scan"
              priority
              className="rounded-[18px] border-0"
              label="Film : l’application desktop scanne un fichier Python en local. Gitleaks repère un secret Stripe ligne 4, Semgrep une injection SQL ligne 10, et aucun octet de code n’est envoyé au serveur."
            />
          </FilmFrame>
        </TiltIn>

        <section className="grid gap-12 py-28" aria-labelledby="pourquoi">
          <SectionTitle pill="Pourquoi CodeShield" id="pourquoi" title="Pensé pour le code qui ne doit pas sortir.">
            La sécurité du code sans confier votre dépôt à un service tiers.
          </SectionTitle>
          <WhyBento />
        </section>

        <section className="grid gap-12 pb-28" aria-labelledby="fonctionnement">
          <SectionTitle pill="Fonctionnement" id="fonctionnement" title="Scanner, corriger, vérifier.">
            Trois temps, et une règle : seule une preuve ferme une faille.
          </SectionTitle>
          <ProcessCards />
        </section>

        {/* Deux rangées texte et film, en alternance. */}
        <section className="grid gap-20 pb-28" aria-label="L’app en action">
          <div className="grid items-center gap-10 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
            <Stagger className="grid content-start justify-items-start gap-5" amount={0.5}>
              <motion.h2 variants={rise} className="lp-text-fade text-[clamp(1.9rem,3.4vw,2.6rem)] leading-[1.08] font-medium tracking-[-0.035em]">
                Seul l’emplacement quitte votre poste.
              </motion.h2>
              <motion.p variants={rise} className="max-w-md text-[16px] leading-relaxed text-fog">
                Deux fiches passent le contrat d’API. Un envoi qui transporte du code est refusé avant d’être lu, et le serveur
                ne stocke aucune ligne.
              </motion.p>
              <motion.a variants={rise} href="#confidentialite" className="lp-btn-ghost lp-sm">
                Ce qui part, ce qui reste
              </motion.a>
            </Stagger>
            <Reveal variants={cardIn} amount={0.35}>
              <FilmFrame>
                <Film
                  id="wire"
                  className="rounded-[18px] border-0"
                  label="Film : deux fiches de métadonnées passent le contrat d’API et deviennent des failles côté serveur. Un envoi contenant un extrait de code est refusé avec une erreur 422. Zéro ligne de code stockée."
                />
              </FilmFrame>
            </Reveal>
          </div>

          <div className="grid items-center gap-10 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]">
            <Reveal variants={cardIn} amount={0.35} className="order-2 lg:order-1">
              <FilmFrame>
                <Film
                  id="fix"
                  className="rounded-[18px] border-0"
                  label="Film : Claude Code lit la fiche de la faille critique, remplace la requête SQL par une requête paramétrée, et le scan suivant ne la retrouve plus : elle passe en résolue."
                />
              </FilmFrame>
            </Reveal>
            <Stagger className="order-1 grid min-w-0 content-start justify-items-start gap-5 lg:order-2" amount={0.4}>
              <motion.h2
                id="claude-code"
                variants={rise}
                className="lp-text-fade scroll-mt-28 text-[clamp(1.9rem,3.4vw,2.6rem)] leading-[1.08] font-medium tracking-[-0.035em]"
              >
                Claude Code corrige, le scan vérifie.
              </motion.h2>
              <motion.p variants={rise} className="max-w-md text-[16px] leading-relaxed text-fog">
                Sélectionnez une faille et cliquez sur « Fix with Claude Code », ou confiez-lui toutes les critiques d’un coup.
                L’app le lance dans votre projet et affiche ce qu’il modifie, puis un nouveau scan confirme.
              </motion.p>
              <motion.ul variants={rise} className="grid gap-2 text-[14.5px] text-fog">
                <li>Lecture et édition de fichiers uniquement, aucune commande lancée.</li>
                <li>Votre code passe par Anthropic via Claude Code, jamais par CodeShield.</li>
              </motion.ul>
              <motion.div variants={rise}>
                <a href="https://claude.com/claude-code" target="_blank" rel="noreferrer" className="lp-btn-ghost lp-sm">
                  Installer Claude Code
                </a>
              </motion.div>
            </Stagger>
          </div>
        </section>

        <section className="grid gap-12 pb-28" aria-labelledby="confidentialite">
          <SectionTitle pill="Confidentialité" id="confidentialite" title="Ce qui part, ce qui reste.">
            La règle est appliquée à chaque étage : l’app ne sérialise pas le code, et l’API refuse tout ce qui en contiendrait.
          </SectionTitle>
          <PayloadTrio />
        </section>

        <section className="grid gap-12 pb-28" aria-labelledby="moteurs">
          <SectionTitle pill="Moteurs" id="moteurs" title="Sept scanners, lancés là où ils servent.">
            L’app détecte ce que contient le projet, installe les binaires manquants et ne lance que les moteurs utiles.
          </SectionTitle>
          <CategoryCards />
        </section>

        <section className="grid scroll-mt-8 gap-12 pb-28" aria-labelledby="telecharger">
          <SectionTitle pill="Télécharger" id="telecharger" title="Installez CodeShield sur votre poste.">
            Gratuit, open source et sans compte : l’app tourne entièrement sur votre machine.
          </SectionTitle>
          <DownloadCards />
          <InstallSteps />
          <p className="text-center text-[14.5px] text-fog">
            Le code source est ouvert : lisez-le, compilez-le vous-même ou contribuez{' '}
            <a href={GITHUB_URL} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-paper underline-offset-4 hover:underline">
              <GithubIcon className="size-3.5" />
              sur GitHub
            </a>
            .
          </p>
        </section>

        <section className="grid gap-12 pb-28" aria-labelledby="questions">
          <SectionTitle pill="Questions" id="questions" title="Questions fréquentes." />
          <Faq />
        </section>

        {/* Appel final. */}
        <Stagger className="pb-12" amount={0.35}>
          <section aria-labelledby="commencer">
            <Card className="grid justify-items-center gap-6 px-6 pt-20 pb-16 text-center" glowAt="50% 0%" glowSize="45% 75%" glow={0.75}>
              <div aria-hidden className="lp-dots absolute inset-x-0 top-0 h-2/3 [mask-image:linear-gradient(to_bottom,black,transparent)]" />
              <motion.span className="lp-orb lp-glow-pulse relative size-16" variants={pop}>
                <ShieldMark className="h-7 w-auto" />
              </motion.span>
              <motion.h2
                id="commencer"
                variants={rise}
                className="lp-text-fade relative max-w-[18ch] text-[clamp(2rem,4vw,3rem)] leading-[1.05] font-medium tracking-[-0.035em]"
              >
                Prêt pour votre premier scan ?
              </motion.h2>
              <motion.p variants={rise} className="relative max-w-md text-[16px] text-fog">
                Téléchargez l’app desktop et choisissez un dossier.
              </motion.p>
              <motion.div variants={rise} className="relative">
                <DownloadButton />
              </motion.div>
            </Card>
          </section>
        </Stagger>
      </main>

      <footer className="relative overflow-hidden rounded-[28px] bg-[#0b0912] ring-1 ring-[rgb(167_139_250/0.12)]">
        <Horizon side="top" className="top-0 h-[90%] opacity-80" />
        <div className="relative mx-auto grid max-w-6xl gap-10 px-6 pt-16 pb-8 md:grid-cols-[minmax(0,1fr)_auto]">
          <div className="grid content-start justify-items-start gap-4">
            <Logo className="text-white" />
            <p className="max-w-sm text-[20px] leading-snug font-medium tracking-[-0.02em] text-white">
              L’audit de sécurité du code source, sur votre machine.
            </p>
            <div className="flex gap-2">
              <a href="#telecharger" className="lp-btn-ghost lp-sm bg-night/40">
                Télécharger
              </a>
              <a href={GITHUB_URL} target="_blank" rel="noreferrer" className="lp-btn-ghost lp-sm gap-2 bg-night/40">
                <GithubIcon className="size-4" />
                GitHub
              </a>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-12 text-[14px]">
            <div className="grid content-start gap-2.5">
              <p className="font-medium text-white">Produit</p>
              <a href="#pourquoi" className="text-paper/75 hover:text-white">Pourquoi CodeShield</a>
              <a href="#fonctionnement" className="text-paper/75 hover:text-white">Fonctionnement</a>
              <a href="#moteurs" className="text-paper/75 hover:text-white">Moteurs</a>
              <a href="#questions" className="text-paper/75 hover:text-white">Questions</a>
            </div>
            <div className="grid content-start gap-2.5">
              <p className="font-medium text-white">Développeurs</p>
              <a href={GITHUB_URL} className="text-paper/75 hover:text-white">Code source (GitHub)</a>
              <a href={`${GITHUB_URL}/releases`} className="text-paper/75 hover:text-white">Versions</a>
              <a href="#claude-code" className="text-paper/75 hover:text-white">Claude Code</a>
              <a href="https://claude.com/claude-code" className="text-paper/75 hover:text-white">Installer Claude Code</a>
            </div>
          </div>
          <p className="border-t border-white/10 pt-6 text-[13px] text-paper/60 md:col-span-2">© 2026 CodeShield</p>
        </div>
      </footer>
    </div>
  )
}
