import { useQuery } from '@tanstack/react-query'
import { createFileRoute, Link } from '@tanstack/react-router'
import { SeverityBar, SeverityTag, StatusTag } from '@/components/domain'
import { CommandBlock, EmptyState, Skeleton } from '@/components/ui/misc'
import { q } from '@/lib/queries'
import { SEVERITY_META, compareSeverity } from '@/lib/severity'
import { ACTIVE_STATUSES, SEVERITIES } from '@/lib/types'
import { location, timeAgo } from '@/lib/utils'

export const Route = createFileRoute('/_app/projects/$projectId/')({
  component: Overview,
})

function Overview() {
  const { projectId } = Route.useParams()
  const metrics = useQuery(q.metrics(projectId))
  const vulns = useQuery(q.vulns(projectId))
  const scans = useQuery(q.scans(projectId))

  const active = (vulns.data ?? []).filter((v) => ACTIVE_STATUSES.includes(v.status))
  const top = [...active].sort((a, b) => compareSeverity(a.severity, b.severity) || b.id - a.id).slice(0, 6)
  const m = metrics.data

  if (scans.data && scans.data.length === 0) {
    return (
      <div className="grid max-w-3xl gap-6 px-4 py-8 sm:px-8">
        <EmptyState title="Aucun scan reçu pour ce projet">
          <p>
            Dans l’app desktop, ouvrez <strong className="text-paper">Synchronisation</strong>, indiquez le projet n°{' '}
            <strong className="text-paper">{projectId}</strong> et une clé API avec le droit « envoyer des scans », puis
            lancez un scan.
          </p>
        </EmptyState>
        <div className="grid gap-2">
          <p className="text-sm text-fog">Pas encore de clé ?</p>
          <Link to="/settings/keys" className="w-fit text-sm text-signal underline-offset-4 hover:underline">
            Créer une clé API
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="grid gap-10 px-4 py-8 sm:px-8 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
      <section className="grid content-start gap-4" aria-labelledby="priorites">
        <div className="flex items-baseline justify-between gap-4">
          <h2 id="priorites" className="text-xl font-semibold">À corriger en priorité</h2>
          <Link
            to="/projects/$projectId/vulnerabilities"
            params={{ projectId }}
            className="text-sm text-signal underline-offset-4 hover:underline"
          >
            Toutes les failles
          </Link>
        </div>
        {vulns.isPending ? (
          <Skeleton className="h-64" />
        ) : top.length === 0 ? (
          <p className="rounded-xl bg-ok/10 px-5 py-4 text-sm ring-1 ring-ok/30">Aucune faille active. Le dernier scan est propre.</p>
        ) : (
          <ul className="divide-y divide-rule rounded-xl ring-1 ring-rule">
            {top.map((v) => (
              <li key={v.id}>
                <Link
                  to="/projects/$projectId/vulnerabilities"
                  params={{ projectId }}
                  search={{ issue: v.id }}
                  className="grid gap-1 px-5 py-3.5 hover:bg-deck/60 sm:grid-cols-[7rem_minmax(0,1fr)_auto] sm:items-center sm:gap-4"
                >
                  <SeverityTag severity={v.severity} />
                  <span className="min-w-0">
                    <span className="block truncate">{v.title}</span>
                    <span className="block truncate font-mono text-[12.5px] text-haze">{location(v)}</span>
                  </span>
                  <StatusTag status={v.status} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <aside className="grid content-start gap-8">
        <section aria-labelledby="repartition" className="grid gap-4">
          <h2 id="repartition" className="text-xl font-semibold">Failles actives</h2>
          {m ? (
            <>
              <SeverityBar counts={m.open_counts} className="h-3" />
              <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
                {SEVERITIES.map((s) => (
                  <div key={s} className="flex items-baseline justify-between border-b border-rule pb-2">
                    <dt className="flex items-center gap-2 text-sm text-fog">
                      <span className="size-2 rounded-full" style={{ background: SEVERITY_META[s].color }} />
                      {SEVERITY_META[s].label}
                    </dt>
                    <dd className="font-display text-xl font-semibold tabular">{m.open_counts[s]}</dd>
                  </div>
                ))}
              </dl>
              <p className="text-sm text-fog">
                {m.resolved_count} résolue{m.resolved_count > 1 ? 's' : ''}, {m.ignored_count} ignorée{m.ignored_count > 1 ? 's' : ''},{' '}
                {m.total_scans} scan{m.total_scans > 1 ? 's' : ''} reçu{m.total_scans > 1 ? 's' : ''}. Score {m.score}/100.
              </p>
            </>
          ) : (
            <Skeleton className="h-40" />
          )}
        </section>

        <section aria-labelledby="claude" className="grid gap-3">
          <h2 id="claude" className="text-xl font-semibold">Corriger avec Claude Code</h2>
          <p className="text-sm text-fog">Depuis le dépôt, une fois CodeShield ajouté comme serveur MCP :</p>
          <CommandBlock command={`claude "Corrige les failles critiques et hautes du projet CodeShield ${projectId}"`} />
          <Link to="/settings/keys" className="w-fit text-sm text-signal underline-offset-4 hover:underline">
            Configurer Claude Code
          </Link>
        </section>

        {scans.data?.[0] ? (
          <p className="text-sm text-haze">
            Dernier scan {timeAgo(scans.data[0].created_at)} ({scans.data[0].findings_count} findings
            {scans.data[0].branch ? ` sur ${scans.data[0].branch}` : ''}).
          </p>
        ) : null}
      </aside>
    </div>
  )
}
