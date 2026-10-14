import { Accordion } from 'radix-ui'
import { Bot, ChevronDown, Laptop } from 'lucide-react'
import { motion } from 'motion/react'
import type { CSSProperties, PointerEvent, ReactNode } from 'react'
import { BrandMark } from '@/components/brand'
import { cn } from '@/lib/utils'
import { CountUp, EASE, cardIn, inView, pop, rise, slide, stagger } from './motion'

/* ------------------------------------------------------------------------- */
/* Primitives                                                                 */
/* ------------------------------------------------------------------------- */

/** Marque seule (le bouclier), pour les pastilles lumineuses. */
export function ShieldMark({ className }: { className?: string }) {
  return <BrandMark className={className} />
}

/** Titre de section centré : une pastille, le titre en dégradé, une phrase. */
export function SectionTitle({ pill, id, title, children }: { pill: string; id: string; title: string; children?: ReactNode }) {
  return (
    <motion.div className="mx-auto grid max-w-2xl justify-items-center gap-4 text-center" variants={stagger(0.09)} {...inView(0.6)}>
      <motion.span className="lp-pill" variants={pop}>
        {pill}
      </motion.span>
      <motion.h2 id={id} variants={rise} className="lp-text-fade scroll-mt-36 text-[clamp(2rem,4vw,3rem)] leading-[1.05] font-medium tracking-[-0.035em]">
        {title}
      </motion.h2>
      {children ? (
        <motion.p variants={rise} className="max-w-lg text-[16px] leading-relaxed text-fog">
          {children}
        </motion.p>
      ) : null}
    </motion.div>
  )
}

export function Card({
  className,
  glowAt,
  glowSize,
  glow,
  hot,
  children,
}: {
  className?: string
  glowAt?: string
  glowSize?: string
  glow?: number
  hot?: boolean
  children: ReactNode
}) {
  const style = { '--glow-at': glowAt, '--glow-size': glowSize, '--glow': glow } as CSSProperties
  return (
    <motion.div className={cn('lp-card', hot && 'lp-card-hot', className)} style={style} variants={cardIn} onPointerMove={spotlight}>
      {children}
    </motion.div>
  )
}

/** La lueur de survol (::before / ::after de .lp-card) suit le pointeur, sans re-rendu. */
function spotlight(e: PointerEvent<HTMLDivElement>) {
  const r = e.currentTarget.getBoundingClientRect()
  e.currentTarget.style.setProperty('--mx', `${e.clientX - r.left}px`)
  e.currentTarget.style.setProperty('--my', `${e.clientY - r.top}px`)
}

function CardText({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="relative grid gap-2">
      <h3 className="text-[21px] leading-tight font-medium tracking-[-0.02em] text-white">{title}</h3>
      <p className="max-w-[30ch] text-[14.5px] leading-relaxed text-fog">{children}</p>
    </div>
  )
}

/* ------------------------------------------------------------------------- */
/* Moteurs : mêmes pastilles-lettres que l'app desktop (Shield.toolBadge).    */
/* ------------------------------------------------------------------------- */

export const ENGINES = [
  { letter: 'O', name: 'Opengrep', color: '#4C9AE8' },
  { letter: 'G', name: 'Gitleaks', color: '#EC7F3C' },
  { letter: 'T', name: 'Trivy', color: '#2CB5A8' },
  { letter: 'V', name: 'OSV-Scanner', color: '#3FB97F' },
  { letter: 'D', name: 'Dependency-Check', color: '#9B8AFB' },
] as const

export function EngineBadge({ letter, color, className }: { letter: string; color: string; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn('inline-grid size-6 shrink-0 place-items-center rounded-md font-mono text-[12px] font-semibold', className)}
      style={{ color, background: `${color}1f`, boxShadow: `inset 0 0 0 1px ${color}40` }}
    >
      {letter}
    </span>
  )
}

export function EngineStrip() {
  return (
    <motion.ul className="flex flex-wrap justify-center gap-x-6 gap-y-3" aria-label="Scanners intégrés" variants={stagger(0.06)}>
      {ENGINES.map((e) => (
        <motion.li key={e.name} variants={pop} className="flex items-center gap-2 text-[15px] font-medium text-paper/85">
          <EngineBadge letter={e.letter} color={e.color} />
          {e.name}
        </motion.li>
      ))}
    </motion.ul>
  )
}

/* ------------------------------------------------------------------------- */
/* Pourquoi CodeShield : bento de quatre cartes illustrées.                   */
/* ------------------------------------------------------------------------- */

export function WhyBento() {
  return (
    <motion.div className="grid gap-4 md:grid-cols-5" variants={stagger(0.12)} {...inView(0.15)}>
      <Card className="min-h-[19rem] p-7 md:col-span-2" glowAt="50% 120%">
        <CardText title="Le scan reste chez vous">Les scanners tournent sur votre machine, dans le dossier du projet.</CardText>
        <div className="absolute inset-x-0 bottom-0 h-44" aria-hidden>
          {[190, 136, 88].map((d, i) => (
            <span
              key={d}
              className="lp-ripple absolute left-1/2 rounded-full"
              style={{
                width: d,
                height: d,
                bottom: 70 - d / 2,
                marginLeft: -d / 2,
                boxShadow: 'inset 0 0 0 1px rgb(167 139 250 / 0.28)',
                animationDelay: `${(2 - i) * 0.45}s`,
              }}
            />
          ))}
          <span className="lp-orb lp-glow-pulse absolute bottom-[38px] left-1/2 size-16 -translate-x-1/2">
            <ShieldMark className="h-7 w-auto" />
          </span>
        </div>
      </Card>

      <Card className="min-h-[19rem] p-7 md:col-span-3" glowAt="50% 85%" glowSize="70% 60%">
        <CardText title="Claude Code corrige en direct">Lancé depuis l’app, il lit chaque faille et modifie le fichier dans votre dépôt.</CardText>
        <div className="absolute inset-x-6 bottom-9 flex items-center justify-center" aria-hidden>
          <Node icon={<Laptop className="size-5" />} label="App desktop" />
          <Wire />
          <motion.span className="grid justify-items-center gap-2" variants={pop}>
            <span className="lp-orb size-16">
              <ShieldMark className="h-7 w-auto" />
            </span>
            <span className="text-[12.5px] text-fog">CodeShield</span>
          </motion.span>
          <Wire delay={1.1} />
          <Node icon={<Bot className="size-5" />} label="Claude Code" />
        </div>
      </Card>

      <Card className="min-h-[19rem] p-7 md:col-span-3" glowAt="85% 55%" glowSize="55% 70%">
        <CardText title="Une note de A à F par projet">Calculée sur les failles encore ouvertes, suivie scan après scan.</CardText>
        <svg viewBox="0 0 400 140" className="absolute right-0 bottom-0 w-[78%]" aria-hidden>
          <defs>
            <linearGradient id="lp-area" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#8b5cf6" stopOpacity="0.35" />
              <stop offset="1" stopColor="#8b5cf6" stopOpacity="0" />
            </linearGradient>
            <filter id="lp-glow" x="-20%" y="-50%" width="140%" height="200%">
              <feGaussianBlur stdDeviation="4" result="b" />
              <feMerge>
                <feMergeNode in="b" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>
          <motion.path
            d="M0 120 L50 112 L90 116 L140 96 L180 100 L230 74 L270 80 L320 46 L360 52 L392 22 L392 140 L0 140 Z"
            fill="url(#lp-area)"
            variants={{ hidden: { opacity: 0 }, show: { opacity: 1, transition: { duration: 1.2, delay: 0.6 } } }}
          />
          <motion.path
            d="M0 120 L50 112 L90 116 L140 96 L180 100 L230 74 L270 80 L320 46 L360 52 L392 22"
            fill="none"
            stroke="#a78bfa"
            strokeWidth="2.5"
            strokeLinejoin="round"
            filter="url(#lp-glow)"
            variants={{ hidden: { pathLength: 0 }, show: { pathLength: 1, transition: { duration: 1.6, ease: EASE } } }}
          />
          <motion.circle
            cx="392"
            cy="22"
            r="5"
            fill="#ede9fe"
            filter="url(#lp-glow)"
            style={{ transformOrigin: '392px 22px' }}
            variants={{ hidden: { scale: 0, opacity: 0 }, show: { scale: 1, opacity: 1, transition: { type: 'spring', stiffness: 400, damping: 14, delay: 1.5 } } }}
          />
        </svg>
        <div className="absolute bottom-6 left-7 grid gap-1 rounded-xl bg-night/70 px-4 py-3 ring-1 ring-[rgb(167_139_250/0.25)] backdrop-blur" aria-hidden>
          <span className="text-[12.5px] text-fog">Note du projet</span>
          <span className="flex items-baseline gap-2">
            <span className="text-[28px] leading-none font-semibold text-[#7FA63A]">B</span>
            <span className="text-[13px] text-fog tabular">
              <CountUp to={84} />
              /100
            </span>
          </span>
        </div>
      </Card>

      <Card className="min-h-[19rem] p-7 md:col-span-2" glowAt="50% 105%">
        <CardText title="La preuve par le scan">Seul le scan suivant peut déclarer une faille résolue.</CardText>
        <ol className="absolute inset-x-7 bottom-7 grid gap-2" aria-label="Statuts successifs">
          {(
            [
              ['Ouverte', '#7684EC', 0.55],
              ['Corrigée, à vérifier', '#A0991F', 0.75],
              ['Résolue', '#2FA67B', 1],
            ] as const
          ).map(([label, dot, o]) => (
            <motion.li
              key={label}
              className="flex h-9 items-center gap-2 rounded-full bg-night/70 px-3.5 text-[13.5px] text-paper ring-1 ring-[rgb(167_139_250/0.22)]"
              custom={o}
              variants={{
                hidden: { opacity: 0, x: -16 },
                show: (opacity: number) => ({ opacity, x: 0, transition: { duration: 0.6, ease: EASE } }),
              }}
            >
              <span className="size-2 rounded-full" style={{ background: dot }} aria-hidden />
              {label}
            </motion.li>
          ))}
        </ol>
      </Card>
    </motion.div>
  )
}

function Node({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <motion.span className="grid justify-items-center gap-2" variants={pop}>
      <span className="grid size-12 place-items-center rounded-full bg-night text-fog ring-1 ring-[rgb(167_139_250/0.3)]">{icon}</span>
      <span className="text-[12.5px] whitespace-nowrap text-fog">{label}</span>
    </motion.span>
  )
}

/** Liaison entre deux nœuds : une impulsion la parcourt de gauche à droite, en boucle. */
function Wire({ delay = 0 }: { delay?: number }) {
  return (
    <span className="relative mx-2 mb-6 h-px min-w-6 flex-1 bg-gradient-to-r from-[rgb(167_139_250/0.15)] via-[#a78bfa] to-[rgb(167_139_250/0.15)]">
      <span className="lp-travel absolute inset-0" style={{ animationDelay: `${delay}s` }}>
        <span className="absolute top-1/2 left-0 size-1.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#ede9fe] shadow-[0_0_10px_#a78bfa]" />
      </span>
    </span>
  )
}

/* ------------------------------------------------------------------------- */
/* Processus : trois temps (c'est une vraie séquence).                         */
/* ------------------------------------------------------------------------- */

function Step({ title, children, art, glowAt = '50% 0%', glow = 0.3 }: { title: string; children: ReactNode; art: ReactNode; glowAt?: string; glow?: number }) {
  return (
    <li className="list-none">
      <Card className="flex h-full min-h-[21rem] flex-col justify-end p-7" glowAt={glowAt} glowSize="80% 60%" glow={glow}>
        {art}
        <CardText title={title}>{children}</CardText>
      </Card>
    </li>
  )
}

export function ProcessCards() {
  return (
    <motion.ol className="grid gap-4 md:grid-cols-3" variants={stagger(0.14)} {...inView(0.2)}>
      <Step
        title="Scannez en local"
        art={
          <div className="absolute inset-x-7 top-7 grid gap-2.5 rounded-xl bg-night/70 p-4 ring-1 ring-[rgb(167_139_250/0.2)]" aria-hidden>
            {(
              [
                ['O', '#4C9AE8', 'Opengrep', 1],
                ['G', '#EC7F3C', 'Gitleaks', 1],
                ['T', '#2CB5A8', 'Trivy', 0.6],
              ] as const
            ).map(([l, c, n, w]) => (
              <motion.div key={n} className="grid gap-1.5" variants={slide}>
                <span className="flex items-center gap-2 text-[12.5px] text-paper">
                  <EngineBadge letter={l} color={c} className="size-5 text-[11px]" />
                  {n}
                </span>
                <span className="h-1 overflow-hidden rounded-full bg-rule">
                  <motion.span
                    className="block h-full origin-left rounded-full bg-[#a78bfa] shadow-[0_0_8px_#a78bfa]"
                    custom={w}
                    variants={{
                      hidden: { scaleX: 0 },
                      show: (scaleX: number) => ({ scaleX, transition: { duration: 1.4, ease: EASE, delay: 0.3 } }),
                    }}
                  />
                </span>
              </motion.div>
            ))}
          </div>
        }
      >
        Choisissez un dossier : l’app lance les moteurs utiles, deux à la fois.
      </Step>
      <Step
        title="Corrigez avec Claude Code"
        art={
          <div className="absolute inset-x-7 top-7 overflow-hidden rounded-xl bg-night/70 py-1 font-mono text-[12px] leading-[2.2] ring-1 ring-[rgb(167_139_250/0.2)]" aria-hidden>
            <motion.div variants={slide} className="truncate bg-sev-critical/15 px-3 text-paper/60 line-through decoration-danger">
              - db.execute(f"…'{'{q}'}'")
            </motion.div>
            <motion.div variants={slide} className="truncate bg-resolved/15 px-3 text-paper">
              + db.execute("…%s", (q,))
            </motion.div>
            <motion.div variants={slide} className="truncate px-3 text-fog">
              <span className="text-syn-kw">→ Edit</span> app/routes/users.py
              <span className="lp-caret ml-0.5 inline-block h-[1.1em] w-[0.5em] translate-y-[0.2em] bg-[#a78bfa]" />
            </motion.div>
          </div>
        }
      >
        Un clic dans l’app : Claude Code lit la faille et modifie le fichier.
      </Step>
      <Step
        title="Le scan suivant confirme"
        glowAt="50% 30%"
        glow={0.45}
        art={
          <div className="absolute inset-x-0 top-9 grid justify-items-center gap-4" aria-hidden>
            <motion.span className="lp-orb lp-glow-pulse size-20" variants={pop}>
              <svg viewBox="0 0 24 24" className="size-8" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round">
                <motion.path
                  d="M20 6 9 17l-5-5"
                  variants={{ hidden: { pathLength: 0 }, show: { pathLength: 1, transition: { duration: 0.7, ease: EASE, delay: 0.5 } } }}
                />
              </svg>
            </motion.span>
            <motion.span
              variants={rise}
              className="inline-flex h-8 items-center gap-2 rounded-full bg-night/80 px-3.5 text-[13px] text-paper ring-1 ring-resolved/50"
            >
              <span className="size-2 rounded-full bg-resolved" />
              Résolue
            </motion.span>
          </div>
        }
      >
        Si la faille a disparu, elle est résolue. Sinon, elle est rouverte.
      </Step>
    </motion.ol>
  )
}

/* ------------------------------------------------------------------------- */
/* Ce qui part, ce qui reste : trois cartes, celle du milieu mise en avant.   */
/* ------------------------------------------------------------------------- */

const kw = 'text-syn-kw'
const str = 'text-syn-str'
const fn = 'text-syn-fn'

const PAYLOAD: [string, string][] = [
  ['tool', '"SEMGREP"'],
  ['file_path', '"app/routes/users.py"'],
  ['line_number', '10'],
  ['severity', '"CRITICAL"'],
  ['cwe', '["CWE-89"]'],
  ['fingerprint', '"9c41e2…a07b"'],
]

export function PayloadTrio() {
  return (
    <motion.div className="grid items-center gap-4 lg:grid-cols-[1fr_1.15fr_1fr]" variants={stagger(0.14)} {...inView(0.2)}>
      <Card className="min-w-0 p-6" glow={0.15}>
        <p className="text-[14px] font-medium text-white">Reste sur votre poste</p>
        <p className="mt-1 text-[13px] text-fog">app/routes/users.py, ligne 10</p>
        <pre className="mt-5 overflow-x-auto rounded-xl bg-night/80 p-4 font-mono text-[12.5px] leading-[1.9] text-paper/90 ring-1 ring-rule">
          <span className={kw}>def</span> <span className={fn}>search</span>(q):{'\n'}
          {'  '}rows = db.execute({'\n'}
          {'    '}
          <span className={str}>f"… name='{'{q}'}'"</span>
          {'\n'}
          {'  '})
        </pre>
        <p className="mt-4 text-[13.5px] leading-relaxed text-fog">Le texte de la ligne sert seulement à calculer l’empreinte, sur votre machine.</p>
      </Card>

      <Card className="min-w-0 p-6" hot glowAt="85% 0%" glowSize="70% 50%" glow={0.4}>
        <p className="text-[14px] font-medium text-white">Part vers CodeShield</p>
        <p className="mt-1 font-mono text-[12.5px] text-fog">POST /api/v1/sync/submit</p>
        <pre className="mt-5 overflow-x-auto rounded-xl bg-night/80 p-4 font-mono text-[12.5px] leading-[1.9] text-fog ring-1 ring-[rgb(167_139_250/0.3)]">
          {'{\n'}
          {PAYLOAD.map(([k, v], i) => (
            <motion.span key={k} variants={{ hidden: { opacity: 0 }, show: { opacity: 1, transition: { duration: 0.4 } } }}>
              {'  '}
              <span className={fn}>"{k}"</span>: <span className={v.startsWith('"') || v.startsWith('[') ? str : 'text-paper'}>{v}</span>
              {i < PAYLOAD.length - 1 ? ',' : ''}
              {'\n'}
            </motion.span>
          ))}
          {'}'}
        </pre>
        <p className="mt-4 text-[13.5px] leading-relaxed text-fog">Des métadonnées et une empreinte sha256 salée, rien d’autre.</p>
      </Card>

      <Card className="min-w-0 p-6" glow={0.15}>
        <p className="text-[14px] font-medium text-white">Refusé par l’API</p>
        <p className="mt-1 text-[13px] text-fog">Tout champ hors contrat</p>
        <pre className="mt-5 overflow-x-auto rounded-xl bg-night/80 p-4 font-mono text-[12.5px] leading-[1.9] text-fog ring-1 ring-danger/30">
          <span className={fn}>"snippet"</span>: <span className={str}>"rows = …"</span>
          {'\n\n'}
          <motion.span
            className="inline-block text-danger"
            variants={{ hidden: { opacity: 0 }, show: { opacity: 1, x: [0, -7, 7, -5, 5, 0], transition: { duration: 0.5, delay: 0.6 } } }}
          >
            422 Unprocessable Entity
          </motion.span>
        </pre>
        <p className="mt-4 text-[13.5px] leading-relaxed text-fog">Un extrait de code est rejeté avant d’être lu ou stocké.</p>
      </Card>
    </motion.div>
  )
}

/* ------------------------------------------------------------------------- */
/* Moteurs par catégorie (couleurs de catégorie du desktop).                  */
/* ------------------------------------------------------------------------- */

const CATEGORIES = [
  { label: 'Code', color: '#6E79D6', engines: ['O'], when: 'Toujours, sur tous les langages du projet.' },
  { label: 'Secrets', color: '#EC7F3C', engines: ['G', 'T'], when: 'Toujours. Les valeurs trouvées sont masquées.' },
  { label: 'Dépendances', color: '#9B8AFB', engines: ['T', 'V', 'D'], when: 'Dès qu’un lockfile ou un manifeste est présent.' },
  { label: 'Infrastructure', color: '#2CB5A8', engines: ['T'], when: 'Terraform, Kubernetes, Dockerfile ou CI.' },
]

export function CategoryCards() {
  return (
    <motion.div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" variants={stagger(0.1)} {...inView(0.2)}>
      {CATEGORIES.map((c) => (
        <Card key={c.label} className="flex flex-col gap-5 p-6" glowAt="100% 0%" glowSize="80% 60%" glow={0.22}>
          <span className="flex items-center gap-2 text-[17px] font-medium text-white">
            <span className="size-2.5 rounded-[4px]" style={{ background: c.color }} aria-hidden />
            {c.label}
          </span>
          <ul className="grid gap-2.5">
            {c.engines.map((l) => {
              const e = ENGINES.find((x) => x.letter === l)!
              return (
                <motion.li key={l} variants={slide} className="flex items-center gap-2.5 text-[14.5px] text-paper/90">
                  <EngineBadge letter={e.letter} color={e.color} />
                  {e.name}
                </motion.li>
              )
            })}
          </ul>
          <p className="mt-auto border-t border-[rgb(167_139_250/0.14)] pt-4 text-[13.5px] leading-relaxed text-fog">{c.when}</p>
        </Card>
      ))}
    </motion.div>
  )
}

/* ------------------------------------------------------------------------- */
/* FAQ                                                                        */
/* ------------------------------------------------------------------------- */

const FAQ: [string, string][] = [
  [
    'Mon code quitte-t-il ma machine ?',
    'Non. Les scanners tournent en local et l’app n’envoie que le fichier, les lignes, la règle, la CWE, la sévérité et une empreinte. L’API rejette tout champ qui pourrait contenir du code.',
  ],
  [
    'Dois-je installer les scanners moi-même ?',
    'Non, et pas besoin de Python. L’app télécharge Opengrep, Gitleaks, Trivy et OSV-Scanner depuis leurs versions publiées, vérifie leur empreinte et les range dans son propre dossier.',
  ],
  [
    'Comment Claude Code corrige-t-il une faille ?',
    'L’app lance le Claude Code installé sur votre poste, dans le dossier du projet, avec la fiche de la faille. Il lit et modifie les fichiers lui-même, sans lancer de commande. Votre code passe par Anthropic via Claude Code, jamais par CodeShield.',
  ],
  [
    'Comment savoir si la correction est bonne ?',
    'Relancez le scan depuis l’app : si la faille a disparu, elle est résolue. Relisez aussi les changements dans votre éditeur ou avec git avant de les valider.',
  ],
  [
    'CodeShield est-il open source ?',
    'Oui. Tout le code (app desktop, site, API) est public sur GitHub : github.com/LordClxt/code-shield. Vous pouvez le lire, compiler l’app vous-même et proposer des améliorations.',
  ],
  [
    'Sur quels systèmes fonctionne l’app ?',
    'macOS (.dmg), Windows (.msi) et Linux (.deb). Claude Code est optionnel : sans lui, l’app scanne et explique les failles.',
  ],
  [
    'Que se passe-t-il si le code se déplace ?',
    'L’empreinte suit la faille même quand ses lignes changent : elle n’est ni dupliquée ni marquée résolue à tort.',
  ],
]

export function Faq() {
  return (
    <Accordion.Root type="single" collapsible defaultValue={FAQ[0]![0]} asChild>
      <motion.div className="mx-auto grid w-full max-w-2xl gap-3" variants={stagger(0.07)} {...inView(0.2)}>
        {FAQ.map(([q, a]) => (
          <Accordion.Item key={q} value={q} asChild>
            <motion.div
              variants={rise}
              className="rounded-2xl bg-[#0d0a16] ring-1 ring-[rgb(167_139_250/0.16)] transition-shadow data-[state=open]:shadow-[inset_0_0_0_1px_rgb(196_181_253/0.55),0_0_40px_-12px_rgb(139_92_246/0.7)]"
            >
              <Accordion.Header>
                <Accordion.Trigger className="group flex w-full items-center justify-between gap-4 rounded-2xl px-5 py-4 text-left text-[15.5px] font-medium text-paper">
                  {q}
                  <ChevronDown className="size-4 shrink-0 text-fog transition-transform duration-300 group-data-[state=open]:rotate-180" aria-hidden />
                </Accordion.Trigger>
              </Accordion.Header>
              {/* Padding sur l'enfant : le conteneur anime sa hauteur jusqu'à 0. */}
              <Accordion.Content className="overflow-hidden text-[14.5px] leading-relaxed text-fog data-[state=closed]:animate-accordion-up data-[state=open]:animate-accordion-down">
                <p className="px-5 pb-5">{a}</p>
              </Accordion.Content>
            </motion.div>
          </Accordion.Item>
        ))}
      </motion.div>
    </Accordion.Root>
  )
}
