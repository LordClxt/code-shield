import { useQuery } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { ErrorState, Skeleton } from '@/components/ui/misc'
import { q } from '@/lib/queries'
import { CATEGORY_LABEL, TOOL_LABEL } from '@/lib/severity'
import type { TimelinePoint } from '@/lib/types'
import { ACTIVE_STATUSES, CATEGORIES } from '@/lib/types'
import { cn } from '@/lib/utils'

type Range = 7 | 30 | 90

export const Route = createFileRoute('/_app/projects/$projectId/metrics')({
  validateSearch: (s: Record<string, unknown>): { days?: Range } => ({
    days: [7, 30, 90].includes(Number(s.days)) ? (Number(s.days) as Range) : undefined,
  }),
  component: Metrics,
})

// Paire validée (bande de luminance, séparation daltonisme) sur les surfaces #141D2E et #0F0F12.
const OPENED = 'var(--color-opened)'
const RESOLVED = 'var(--color-resolved)'

const dayFmt = new Intl.DateTimeFormat('fr', { day: 'numeric', month: 'short' })

function Metrics() {
  const { projectId } = Route.useParams()
  const { days = 30 } = Route.useSearch()
  const navigate = Route.useNavigate()
  const timeline = useQuery(q.timeline(projectId, days))
  const vulns = useQuery(q.vulns(projectId))
  const scans = useQuery(q.scans(projectId))

  const active = (vulns.data ?? []).filter((v) => ACTIVE_STATUSES.includes(v.status))
  const byTool = Object.entries(
    active.reduce<Record<string, number>>((acc, v) => ({ ...acc, [v.tool]: (acc[v.tool] ?? 0) + 1 }), {}),
  ).sort((a, b) => b[1] - a[1])
  const byCat = CATEGORIES.map((c) => [c, active.filter((v) => v.category === c).length] as const)

  const points = timeline.data ?? []
  const opened = points.reduce((n, p) => n + p.opened, 0)
  const resolved = points.reduce((n, p) => n + p.resolved, 0)

  return (
    <div className="grid gap-10 px-4 py-8 sm:px-8">
      <section className="grid gap-4" aria-labelledby="flux">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 id="flux" className="text-xl font-semibold">Nouvelles et corrigées</h2>
            <p className="mt-1 text-sm text-fog">
              {timeline.data
                ? `${opened} nouvelle${opened > 1 ? 's' : ''} et ${resolved} corrigée${resolved > 1 ? 's' : ''} sur ${days} jours.`
                : ' '}
            </p>
          </div>
          <div className="inline-flex rounded-lg bg-night p-1 ring-1 ring-rule" role="group" aria-label="Période">
            {([7, 30, 90] as const).map((d) => (
              <button
                key={d}
                type="button"
                aria-pressed={days === d}
                onClick={() => navigate({ search: { days: d === 30 ? undefined : d }, replace: true })}
                className={cn('rounded-md px-3 py-1 text-[13px] text-fog hover:text-paper', days === d && 'bg-deck-2 text-paper')}
              >
                {d} jours
              </button>
            ))}
          </div>
        </div>
        {timeline.isError ? (
          <ErrorState error={timeline.error} onRetry={() => timeline.refetch()} />
        ) : !timeline.data ? (
          <Skeleton className="h-72" />
        ) : (
          <FlowChart points={points} />
        )}
      </section>

      <div className="grid gap-10 lg:grid-cols-2">
        <section className="grid content-start gap-4" aria-labelledby="outils">
          <h2 id="outils" className="text-xl font-semibold">Failles actives par scanner</h2>
          {vulns.data ? (
            byTool.length ? (
              <HBars rows={byTool.map(([t, n]) => [TOOL_LABEL[t as keyof typeof TOOL_LABEL], n])} />
            ) : (
              <p className="text-sm text-fog">Aucune faille active.</p>
            )
          ) : (
            <Skeleton className="h-40" />
          )}
        </section>
        <section className="grid content-start gap-4" aria-labelledby="familles">
          <h2 id="familles" className="text-xl font-semibold">Par famille</h2>
          {vulns.data ? <HBars rows={byCat.map(([c, n]) => [CATEGORY_LABEL[c], n])} /> : <Skeleton className="h-40" />}
        </section>
      </div>

      <section className="grid gap-4" aria-labelledby="scans">
        <h2 id="scans" className="text-xl font-semibold">Derniers scans</h2>
        {scans.data?.length ? (
          <div className="overflow-x-auto rounded-xl ring-1 ring-rule">
            <table className="w-full min-w-[28rem] text-left text-sm">
              <thead className="bg-deck text-[13px] text-fog">
                <tr>
                  <th scope="col" className="px-4 py-2.5 font-medium">Date</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Branche</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Commit</th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">Findings</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-rule">
                {scans.data.slice(0, 15).map((s) => (
                  <tr key={s.id}>
                    <td className="px-4 py-2.5 whitespace-nowrap">{new Date(s.created_at).toLocaleString('fr')}</td>
                    <td className="px-4 py-2.5 text-fog">{s.branch ?? '—'}</td>
                    <td className="px-4 py-2.5 font-mono text-[12.5px] text-fog">{s.commit_sha?.slice(0, 8) ?? '—'}</td>
                    <td className="px-4 py-2.5 text-right tabular">{s.findings_count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : scans.data ? (
          <p className="text-sm text-fog">Aucun scan reçu pour l’instant.</p>
        ) : (
          <Skeleton className="h-40" />
        )}
      </section>
    </div>
  )
}

function FlowChart({ points }: { points: TimelinePoint[] }) {
  const data = points.map((p) => ({ ...p, label: dayFmt.format(new Date(`${p.date}T00:00:00Z`)) }))
  return (
    <figure className="grid gap-3 rounded-xl bg-deck p-4 ring-1 ring-rule">
      <figcaption className="flex gap-5 text-[13px] text-fog">
        <span className="inline-flex items-center gap-2">
          <span className="size-2.5 rounded-sm" style={{ background: OPENED }} /> Nouvelles
        </span>
        <span className="inline-flex items-center gap-2">
          <span className="size-2.5 rounded-sm" style={{ background: RESOLVED }} /> Corrigées
        </span>
      </figcaption>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} barGap={2} barCategoryGap="22%" margin={{ top: 8, right: 4, bottom: 0, left: -18 }}>
            <CartesianGrid vertical={false} stroke="var(--color-rule)" strokeDasharray="0" />
            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: 'var(--color-haze)', fontSize: 12 }} minTickGap={24} />
            <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: 'var(--color-haze)', fontSize: 12 }} />
            <Tooltip
              cursor={{ fill: 'color-mix(in oklab, var(--color-rule) 50%, transparent)' }}
              content={({ active, payload, label }) =>
                active && payload?.length ? (
                  <div className="rounded-md bg-deck-2 px-3 py-2 text-[13px] ring-1 ring-rule-strong">
                    <p className="mb-1 text-fog">{label}</p>
                    <p>{`${payload[0]?.value ?? 0} nouvelle(s)`}</p>
                    <p>{`${payload[1]?.value ?? 0} corrigée(s)`}</p>
                  </div>
                ) : null
              }
            />
            <Bar dataKey="opened" name="Nouvelles" fill={OPENED} radius={[4, 4, 0, 0]} maxBarSize={18} />
            <Bar dataKey="resolved" name="Corrigées" fill={RESOLVED} radius={[4, 4, 0, 0]} maxBarSize={18} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      {/* Vue tableau accessible */}
      <details className="text-[13px] text-fog">
        <summary className="cursor-pointer hover:text-paper">Voir les données</summary>
        <table className="mt-2 w-full max-w-sm">
          <thead>
            <tr>
              <th scope="col" className="text-left font-medium">Jour</th>
              <th scope="col" className="text-right font-medium">Nouvelles</th>
              <th scope="col" className="text-right font-medium">Corrigées</th>
            </tr>
          </thead>
          <tbody>
            {data.filter((d) => d.opened || d.resolved).map((d) => (
              <tr key={d.date}>
                <td>{d.label}</td>
                <td className="text-right tabular">{d.opened}</td>
                <td className="text-right tabular">{d.resolved}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  )
}

/** Barres horizontales d'une seule teinte, étiquetées directement (pas de légende nécessaire). */
function HBars({ rows }: { rows: (readonly [string, number])[] }) {
  const max = Math.max(1, ...rows.map((r) => r[1]))
  return (
    <ul className="grid gap-2.5">
      {rows.map(([label, n]) => (
        <li key={label} className="grid grid-cols-[8.5rem_minmax(0,1fr)_2.5rem] items-center gap-3 text-sm">
          <span className="truncate text-fog">{label}</span>
          <span className="h-2.5 rounded-r-[4px] bg-signal/80" style={{ width: `${(n / max) * 100}%`, minWidth: n ? 4 : 0 }} />
          <span className="text-right tabular">{n}</span>
        </li>
      ))}
    </ul>
  )
}
