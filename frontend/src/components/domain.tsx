import { GRADE_META, type Grade } from '@/lib/score'
import { CATEGORY_LABEL, SEVERITY_META, STATUS_META, TOOL_LABEL } from '@/lib/severity'
import type { Category, Severity, ToolName, VulnStatus } from '@/lib/types'
import { SEVERITIES } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Tooltip } from './ui/overlay'

/** Marque : un bouclier dessiné comme une gouttière de code (lignes + un repère). */
export function Logo({ className, withName = true }: { className?: string; withName?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <svg viewBox="0 0 28 32" className="h-7 w-auto" aria-hidden>
        <path d="M14 1.5 26 6v10.2c0 7.2-5 12.6-12 14.3C7 28.8 2 23.4 2 16.2V6z" fill="none" stroke="currentColor" strokeWidth="2" className="text-paper" />
        <path d="M8.5 11h9M8.5 15.5h11M8.5 20h6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="text-fog" />
        <circle cx="21.5" cy="20" r="2.4" className="fill-sev-critical" />
      </svg>
      {withName ? <span className="font-display text-[19px] font-semibold tracking-tight">CodeShield</span> : null}
    </span>
  )
}

/** Sévérité : pastille colorée + libellé (jamais la couleur seule). */
export function SeverityTag({ severity, className }: { severity: Severity; className?: string }) {
  const m = SEVERITY_META[severity]
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-[13px] font-medium text-paper', className)}>
      <span className="size-2 rounded-full" style={{ background: m.color }} aria-hidden />
      {m.label}
    </span>
  )
}

const STATUS_TONE = {
  open: 'text-paper ring-rule-strong',
  progress: 'text-signal ring-signal/40',
  pending: 'text-[#d9c86a] ring-[#a0991f]/50',
  done: 'text-ok ring-ok/40',
  muted: 'text-haze ring-rule',
} as const

export function StatusTag({ status }: { status: VulnStatus }) {
  const m = STATUS_META[status]
  return (
    <span className={cn('inline-flex h-6 items-center rounded-full px-2.5 text-[12px] font-medium ring-1 whitespace-nowrap', STATUS_TONE[m.tone])}>
      {m.label}
    </span>
  )
}

export function ToolTag({ tool }: { tool: ToolName }) {
  return <span className="text-[13px] text-fog">{TOOL_LABEL[tool]}</span>
}

export function CategoryTag({ category }: { category: Category }) {
  return (
    <span className="inline-flex h-6 items-center rounded-md bg-night px-2 text-[12px] text-fog ring-1 ring-rule">
      {CATEGORY_LABEL[category]}
    </span>
  )
}

/** Note A–F : la lettre en grand, colorée, avec son libellé en aide. */
export function GradeMark({ grade, score, size = 'md' }: { grade: Grade; score?: number; size?: 'md' | 'lg' }) {
  const m = GRADE_META[grade]
  return (
    <Tooltip content={`Note ${grade} : ${m.label}${score !== undefined ? ` (${score}/100)` : ''}`}>
      <span
        className={cn(
          'inline-grid place-items-center rounded-lg font-display font-bold ring-1',
          size === 'lg' ? 'size-16 text-[40px]' : 'size-11 text-[26px]',
        )}
        style={{ color: m.color, background: `${m.color}1a`, boxShadow: `inset 0 0 0 1px ${m.color}55` }}
      >
        <span className="sr-only">Note </span>
        {grade}
      </span>
    </Tooltip>
  )
}

/**
 * Barre empilée des failles actives par sévérité (gap de 2px entre segments).
 * Les comptes sont lisibles au survol et en texte alternatif.
 */
export function SeverityBar({ counts, className }: { counts: Partial<Record<Severity, number>>; className?: string }) {
  const total = SEVERITIES.reduce((s, k) => s + (counts[k] ?? 0), 0)
  const label = SEVERITIES.map((s) => `${counts[s] ?? 0} ${SEVERITY_META[s].label.toLowerCase()}`).join(', ')
  if (total === 0) {
    return (
      <div className={cn('flex h-2 items-center', className)} aria-label="Aucune faille active">
        <div className="h-0.5 w-full rounded-full bg-ok/50" />
      </div>
    )
  }
  return (
    <div className={cn('flex h-2 gap-0.5', className)} role="img" aria-label={label}>
      {SEVERITIES.filter((s) => (counts[s] ?? 0) > 0).map((s) => (
        <Tooltip key={s} content={`${counts[s]} ${SEVERITY_META[s].label.toLowerCase()}`}>
          <div
            className="h-full rounded-[3px] first:rounded-l-full last:rounded-r-full"
            style={{ flexGrow: counts[s] ?? 0, background: SEVERITY_META[s].color }}
          />
        </Tooltip>
      ))}
    </div>
  )
}

/** Sparkline SVG (nombre de findings par scan, du plus ancien au plus récent). */
export function Sparkline({ values, className, label }: { values: number[]; className?: string; label: string }) {
  const w = 120
  const h = 32
  if (values.length < 2) {
    return <span className={cn('text-[13px] text-haze', className)}>{values.length === 1 ? '1 scan' : 'Aucun scan'}</span>
  }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  const pts = values.map((v, i) => [(i / (values.length - 1)) * (w - 4) + 2, h - 3 - ((v - min) / span) * (h - 6)] as const)
  const last = pts[pts.length - 1]!
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className={cn('h-8 w-30', className)} role="img" aria-label={label}>
      <polyline points={pts.map((p) => p.join(',')).join(' ')} fill="none" stroke="var(--color-fog)" strokeWidth="1.6" strokeLinejoin="round" />
      <circle cx={last[0]} cy={last[1]} r="2.8" fill="var(--color-paper)" />
    </svg>
  )
}
