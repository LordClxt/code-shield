import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import {
  type ColumnDef,
  type SortingState,
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { CategoryTag, SeverityTag, StatusTag, ToolTag } from '@/components/domain'
import { Button } from '@/components/ui/button'
import { Input, Textarea } from '@/components/ui/form'
import { CopyButton, EmptyState, ErrorState, Skeleton } from '@/components/ui/misc'
import { Sheet, SheetContent } from '@/components/ui/overlay'
import { api } from '@/lib/api'
import { q } from '@/lib/queries'
import { SEVERITY_META, SEVERITY_ORDER, TOOL_LABEL } from '@/lib/severity'
import type { IssueDetail, Severity, Vulnerability, VulnStatus } from '@/lib/types'
import { ACTIVE_STATUSES, SEVERITIES } from '@/lib/types'
import { cn, location, timeAgo } from '@/lib/utils'

type StatusFilter = 'active' | 'resolved' | 'ignored' | 'all'

interface Search {
  sev?: Severity[]
  status?: StatusFilter
  q?: string
  tool?: string
  issue?: number
}

export const Route = createFileRoute('/_app/projects/$projectId/vulnerabilities')({
  validateSearch: (s: Record<string, unknown>): Search => ({
    sev: Array.isArray(s.sev) ? (s.sev.filter((x) => SEVERITIES.includes(x as Severity)) as Severity[]) : undefined,
    status: ['active', 'resolved', 'ignored', 'all'].includes(s.status as string) ? (s.status as StatusFilter) : undefined,
    q: typeof s.q === 'string' && s.q ? s.q : undefined,
    tool: typeof s.tool === 'string' && s.tool ? s.tool : undefined,
    issue: typeof s.issue === 'number' ? s.issue : undefined,
  }),
  component: Vulnerabilities,
})

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'active', label: 'Actives' },
  { value: 'resolved', label: 'Résolues' },
  { value: 'ignored', label: 'Ignorées' },
  { value: 'all', label: 'Toutes' },
]

function matchesStatus(s: VulnStatus, f: StatusFilter) {
  if (f === 'all') return true
  if (f === 'resolved') return s === 'RESOLVED'
  if (f === 'ignored') return s === 'IGNORED'
  return ACTIVE_STATUSES.includes(s)
}

const columns: ColumnDef<Vulnerability>[] = [
  {
    id: 'severity',
    header: 'Sévérité',
    accessorFn: (v) => SEVERITY_ORDER[v.severity],
    cell: ({ row }) => <SeverityTag severity={row.original.severity} />,
    size: 120,
  },
  {
    id: 'title',
    header: 'Faille',
    accessorFn: (v) => v.title,
    cell: ({ row }) => (
      <div className="min-w-0">
        <div className="truncate text-paper">{row.original.title}</div>
        <div className="truncate font-mono text-[12.5px] text-haze">{location(row.original)}</div>
      </div>
    ),
  },
  { id: 'tool', header: 'Scanner', accessorFn: (v) => TOOL_LABEL[v.tool], cell: ({ row }) => <ToolTag tool={row.original.tool} />, size: 140 },
  { id: 'status', header: 'Statut', accessorFn: (v) => v.status, cell: ({ row }) => <StatusTag status={row.original.status} />, size: 170 },
  {
    id: 'last_seen',
    header: 'Vue',
    accessorFn: (v) => v.last_seen,
    cell: ({ row }) => <span className="text-[13px] whitespace-nowrap text-fog">{timeAgo(row.original.last_seen)}</span>,
    size: 120,
  },
]

function Vulnerabilities() {
  const { projectId } = Route.useParams()
  const search = Route.useSearch()
  const navigate = useNavigate({ from: Route.fullPath })
  const vulns = useQuery(q.vulns(projectId))
  const [sorting, setSorting] = useState<SortingState>([{ id: 'severity', desc: false }])

  const status = search.status ?? 'active'
  const sevs = search.sev ?? []
  const needle = (search.q ?? '').toLowerCase()

  const all = vulns.data ?? []
  const rows = useMemo(
    () =>
      all.filter(
        (v) =>
          matchesStatus(v.status, status) &&
          (sevs.length === 0 || sevs.includes(v.severity)) &&
          (!search.tool || v.tool === search.tool) &&
          (!needle || `${v.title} ${v.rule_id} ${v.file_path} ${v.cwe.join(' ')} ${v.package ?? ''}`.toLowerCase().includes(needle)),
      ),
    [all, status, sevs, search.tool, needle],
  )
  const sevCounts = useMemo(() => {
    const c: Record<Severity, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
    for (const v of all) if (matchesStatus(v.status, status)) c[v.severity]++
    return c
  }, [all, status])
  const tools = useMemo(() => [...new Set(all.map((v) => v.tool))], [all])

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: { pagination: { pageSize: 50 } },
  })

  const setSearch = (patch: Partial<Search>) => navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true })

  return (
    <div className="grid gap-5 px-4 py-6 sm:px-8">
      {/* Filtres : une seule rangée au-dessus du tableau */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-72">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-haze" aria-hidden />
          <Input
            aria-label="Rechercher une faille"
            placeholder="Fichier, règle, CWE, paquet…"
            className="pl-9"
            defaultValue={search.q}
            onChange={(e) => setSearch({ q: e.target.value || undefined })}
          />
        </div>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filtrer par sévérité">
          {SEVERITIES.map((s) => {
            const on = sevs.includes(s)
            return (
              <button
                key={s}
                type="button"
                aria-pressed={on}
                onClick={() => setSearch({ sev: on ? sevs.filter((x) => x !== s) : [...sevs, s] })}
                className={cn(
                  'inline-flex h-8 items-center gap-2 rounded-full px-3 text-[13px] ring-1 transition-colors',
                  on ? 'bg-deck-2 text-paper ring-rule-strong' : 'text-fog ring-rule hover:text-paper',
                )}
              >
                <span className="size-2 rounded-full" style={{ background: SEVERITY_META[s].color }} aria-hidden />
                {SEVERITY_META[s].label}
                <span className="text-haze tabular">{sevCounts[s]}</span>
              </button>
            )
          })}
        </div>
        <select
          aria-label="Filtrer par scanner"
          value={search.tool ?? ''}
          onChange={(e) => setSearch({ tool: e.target.value || undefined })}
          className="h-8 rounded-md bg-night px-2 text-[13px] text-fog ring-1 ring-rule"
        >
          <option value="">Tous les scanners</option>
          {tools.map((t) => (
            <option key={t} value={t}>
              {TOOL_LABEL[t]}
            </option>
          ))}
        </select>
        <div className="ml-auto inline-flex rounded-lg bg-night p-1 ring-1 ring-rule" role="group" aria-label="Filtrer par statut">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              aria-pressed={status === f.value}
              onClick={() => setSearch({ status: f.value === 'active' ? undefined : f.value })}
              className={cn(
                'rounded-md px-3 py-1 text-[13px] text-fog hover:text-paper',
                status === f.value && 'bg-deck-2 text-paper',
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {vulns.isPending ? (
        <Skeleton className="h-96" />
      ) : vulns.isError ? (
        <ErrorState error={vulns.error} onRetry={() => vulns.refetch()} />
      ) : all.length === 0 ? (
        <EmptyState title="Aucune faille enregistrée">Les failles apparaîtront après la première synchronisation depuis l’app desktop.</EmptyState>
      ) : (
        <div className="overflow-hidden rounded-xl ring-1 ring-rule">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[46rem] table-fixed text-left text-sm">
              <thead className="bg-deck text-[13px] text-fog">
                {table.getHeaderGroups().map((hg) => (
                  <tr key={hg.id}>
                    {hg.headers.map((h) => {
                      const dir = h.column.getIsSorted()
                      return (
                        <th
                          key={h.id}
                          scope="col"
                          style={{ width: h.column.columnDef.size }}
                          aria-sort={dir === 'asc' ? 'ascending' : dir === 'desc' ? 'descending' : 'none'}
                          className="px-4 py-2.5 font-medium"
                        >
                          <button type="button" onClick={h.column.getToggleSortingHandler()} className="inline-flex items-center gap-1 hover:text-paper">
                            {flexRender(h.column.columnDef.header, h.getContext())}
                            {dir === 'asc' ? <ArrowUp className="size-3.5" /> : dir === 'desc' ? <ArrowDown className="size-3.5" /> : null}
                          </button>
                        </th>
                      )
                    })}
                  </tr>
                ))}
              </thead>
              <tbody className="divide-y divide-rule">
                {table.getRowModel().rows.map((row) => (
                  <tr
                    key={row.id}
                    tabIndex={0}
                    onClick={() => setSearch({ issue: row.original.id })}
                    onKeyDown={(e) => e.key === 'Enter' && setSearch({ issue: row.original.id })}
                    className={cn('cursor-pointer hover:bg-deck/60 focus-visible:bg-deck/60', search.issue === row.original.id && 'bg-deck-2')}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id} className="px-4 py-3 align-middle">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                ))}
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={columns.length} className="px-4 py-10 text-center text-fog">
                      Aucune faille ne correspond à ces filtres.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          {table.getPageCount() > 1 ? (
            <div className="flex items-center justify-between border-t border-rule bg-deck/50 px-4 py-2 text-[13px] text-fog">
              <span>
                {rows.length} failles, page {table.getState().pagination.pageIndex + 1} sur {table.getPageCount()}
              </span>
              <span className="flex gap-1">
                <Button size="icon" variant="ghost" onClick={() => table.previousPage()} disabled={!table.getCanPreviousPage()} aria-label="Page précédente">
                  <ChevronLeft />
                </Button>
                <Button size="icon" variant="ghost" onClick={() => table.nextPage()} disabled={!table.getCanNextPage()} aria-label="Page suivante">
                  <ChevronRight />
                </Button>
              </span>
            </div>
          ) : null}
        </div>
      )}

      <Sheet open={search.issue !== undefined} onOpenChange={(o) => !o && setSearch({ issue: undefined })}>
        {search.issue !== undefined ? <IssueSheet id={search.issue} projectId={projectId} /> : null}
      </Sheet>
    </div>
  )
}

function IssueSheet({ id, projectId }: { id: number; projectId: number }) {
  const issue = useQuery(q.issue(id))
  const d = issue.data
  return (
    <SheetContent
      title={d ? d.title : 'Chargement…'}
      description={
        d ? (
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <SeverityTag severity={d.severity} />
            <StatusTag status={d.status} />
            <CategoryTag category={d.category} />
            <ToolTag tool={d.tool} />
          </span>
        ) : undefined
      }
    >
      {issue.isError ? (
        <div className="p-6">
          <ErrorState error={issue.error} onRetry={() => issue.refetch()} />
        </div>
      ) : !d ? (
        <div className="grid gap-4 p-6">
          <Skeleton className="h-20" />
          <Skeleton className="h-40" />
        </div>
      ) : (
        <IssueBody d={d} projectId={projectId} />
      )}
    </SheetContent>
  )
}

function IssueBody({ d, projectId }: { d: IssueDetail; projectId: number }) {
  const fc = d.fix_context
  const prompt = `Corrige la faille CodeShield n° ${d.id} (${location(d)}${d.cwe.length ? `, ${d.cwe.join(', ')}` : ''}) : utilise get_issue du serveur MCP codeshield pour la marche à suivre, applique le correctif minimal, lance les tests, puis mark_issue_fixed.`
  return (
    <div className="grid gap-7 p-6">
      {/* Emplacement : ce que l'agent ou le dev ouvrira localement */}
      <section className="grid gap-2" aria-labelledby="ou">
        <h3 id="ou" className="font-sans text-[13px] font-medium text-fog">Emplacement</h3>
        <div className="flex items-center gap-2 rounded-lg bg-night px-3 py-2.5 ring-1 ring-rule">
          <code className="min-w-0 flex-1 truncate font-mono text-[13px]">
            {fc.file_path}
            <span className="text-signal">
              :{fc.line_start}
              {fc.line_end !== fc.line_start ? `-${fc.line_end}` : ''}
            </span>
            {fc.column ? <span className="text-haze">{`, col. ${fc.column}`}</span> : null}
          </code>
          <CopyButton value={`${fc.file_path}:${fc.line_start}`} />
        </div>
        <p className="font-mono text-[12.5px] break-all text-haze">{d.rule_id}</p>
        {d.package ? (
          <p className="text-sm">
            Paquet <span className="font-mono">{d.package}</span>
            {d.installed_version ? ` ${d.installed_version}` : ''}
            {d.fixed_version ? (
              <>
                {', corrigé en '}
                <span className="font-mono text-ok">{d.fixed_version}</span>
              </>
            ) : ', aucune version corrigée connue'}
          </p>
        ) : null}
      </section>

      <section className="grid gap-3" aria-labelledby="quoi">
        <h3 id="quoi" className="text-lg font-semibold">{fc.guidance.category}</h3>
        <p className="text-[14.5px] text-paper/90"><InlineCode text={fc.guidance.what} /></p>
        <p className="text-[14.5px] text-fog"><InlineCode text={fc.guidance.why} /></p>
        {d.message ? <p className="rounded-lg bg-night p-3 text-[13px] whitespace-pre-line text-fog ring-1 ring-rule">{d.message}</p> : null}
      </section>

      <section className="grid gap-3" aria-labelledby="corriger">
        <h3 id="corriger" className="font-sans text-[15px] font-semibold">Comment corriger</h3>
        <ol className="grid list-decimal gap-2 pl-5 text-[14.5px] marker:text-haze">
          {fc.guidance.fix.map((step) => (
            <li key={step}><InlineCode text={step} /></li>
          ))}
        </ol>
        {fc.guidance.references.length ? (
          <ul className="flex flex-wrap gap-x-4 gap-y-1 text-[13px]">
            {fc.guidance.references.map((r) => (
              <li key={r}>
                <a href={r} target="_blank" rel="noreferrer" className="text-signal underline-offset-4 hover:underline">
                  {new URL(r).hostname.replace(/^www\./, '')}
                </a>
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      <section className="grid gap-2 rounded-xl bg-night p-4 ring-1 ring-signal/30" aria-labelledby="claude">
        <h3 id="claude" className="font-sans text-[15px] font-semibold">Confier à Claude Code</h3>
        <p className="text-[13.5px] text-fog">À coller dans Claude Code, lancé dans le dépôt avec le serveur MCP CodeShield.</p>
        <p className="rounded-md bg-deck p-3 text-[13px] text-paper/90">{prompt}</p>
        <CopyButton value={prompt} label="Copier la consigne" className="justify-self-start" />
      </section>

      <StatusActions d={d} projectId={projectId} />

      <p className="text-[13px] text-haze">
        Détectée {timeAgo(d.first_seen)}, vue {timeAgo(d.last_seen)}
        {d.resolved_by ? `. Dernière action : ${d.resolved_by}` : ''}
        {d.resolution_note ? ` (« ${d.resolution_note} »)` : ''}.
      </p>
    </div>
  )
}

function StatusActions({ d, projectId }: { d: IssueDetail; projectId: number }) {
  const queryClient = useQueryClient()
  const [ignoring, setIgnoring] = useState(false)
  const [note, setNote] = useState('')
  const update = useMutation({
    mutationFn: (v: { status: VulnStatus; note?: string }) => api.updateIssue(d.id, v.status, v.note),
    onSuccess: async (next) => {
      queryClient.setQueryData(q.issue(d.id).queryKey, next)
      await queryClient.invalidateQueries({ queryKey: ['projects', projectId] })
      setIgnoring(false)
      setNote('')
      toast.success(`Statut : ${next.status === 'IGNORED' ? 'ignorée' : next.status === 'RESOLVED' ? 'résolue' : next.status === 'IN_PROGRESS' ? 'en cours' : next.status === 'FIXED_PENDING_VERIFY' ? 'corrigée, à vérifier' : 'ouverte'}.`)
    },
    onError: (e) => toast.error(e.message),
  })
  const active = ACTIVE_STATUSES.includes(d.status)

  return (
    <section className="grid gap-3 border-t border-rule pt-6" aria-labelledby="statut">
      <h3 id="statut" className="font-sans text-[15px] font-semibold">Changer le statut</h3>
      <div className="flex flex-wrap gap-2">
        {active && d.status !== 'IN_PROGRESS' ? (
          <Button size="sm" onClick={() => update.mutate({ status: 'IN_PROGRESS' })} disabled={update.isPending}>
            Je m’en occupe
          </Button>
        ) : null}
        {active && d.status !== 'FIXED_PENDING_VERIFY' ? (
          <Button size="sm" onClick={() => update.mutate({ status: 'FIXED_PENDING_VERIFY' })} disabled={update.isPending}>
            Marquer corrigée
          </Button>
        ) : null}
        {active ? (
          <Button size="sm" variant="ghost" onClick={() => setIgnoring((v) => !v)}>
            Ignorer
          </Button>
        ) : (
          <Button size="sm" onClick={() => update.mutate({ status: 'OPEN' })} disabled={update.isPending}>
            Rouvrir
          </Button>
        )}
      </div>
      <p className="text-[13px] text-haze">« Marquer corrigée » est confirmé par le prochain scan : la faille passe alors en résolue, ou revient si elle est encore là.</p>
      {ignoring ? (
        <form
          className="grid gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            update.mutate({ status: 'IGNORED', note })
          }}
        >
          <label htmlFor="justif" className="text-[13px] text-fog">
            Pourquoi ignorer cette faille ? Faux positif, risque accepté… (10 caractères minimum)
          </label>
          <Textarea id="justif" value={note} onChange={(e) => setNote(e.target.value)} required minLength={10} autoFocus />
          <Button type="submit" size="sm" variant="danger" className="justify-self-start" disabled={note.trim().length < 10 || update.isPending}>
            Ignorer la faille
          </Button>
        </form>
      ) : null}
    </section>
  )
}

/** Rend les `extraits` entre backticks de la guidance backend en police code. */
function InlineCode({ text }: { text: string }) {
  return (
    <>
      {text.split('`').map((part, i) =>
        i % 2 === 1 ? (
          <code key={i} className="rounded bg-night px-1 py-0.5 font-mono text-[0.88em] ring-1 ring-rule">
            {part}
          </code>
        ) : (
          part
        ),
      )}
    </>
  )
}
