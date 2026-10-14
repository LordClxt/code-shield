import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { GradeMark, SeverityBar, Sparkline } from '@/components/domain'
import { PageHeader } from '@/components/page-header'
import { Button } from '@/components/ui/button'
import { Field, Input } from '@/components/ui/form'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/misc'
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/overlay'
import { api } from '@/lib/api'
import { q } from '@/lib/queries'
import type { Grade } from '@/lib/score'
import { SEVERITY_META } from '@/lib/severity'
import type { Project } from '@/lib/types'
import { SEVERITIES } from '@/lib/types'
import { timeAgo } from '@/lib/utils'

export const Route = createFileRoute('/_app/dashboard')({
  loader: ({ context }) => context.queryClient.ensureQueryData(q.projects()),
  head: () => ({ meta: [{ title: 'Projets · CodeShield' }] }),
  component: Dashboard,
})

function Dashboard() {
  const projects = useQuery(q.projects())
  const list = projects.data ?? []
  const metrics = useQueries({ queries: list.map((p) => q.metrics(p.id)) })
  const scans = useQueries({ queries: list.map((p) => q.scans(p.id)) })

  // Synthèse organisation : pire note et total des failles actives.
  const totals = SEVERITIES.map((s) => metrics.reduce((n, m) => n + (m.data?.open_counts[s] ?? 0), 0))
  const openTotal = totals.reduce((a, b) => a + b, 0)
  const critical = totals[0] ?? 0

  return (
    <>
      <PageHeader
        title="Projets"
        description={
          list.length === 0
            ? 'Créez un projet, puis synchronisez un scan depuis l’app desktop.'
            : openTotal === 0
              ? `${list.length} projet${list.length > 1 ? 's' : ''}, aucune faille active.`
              : `${openTotal} faille${openTotal > 1 ? 's' : ''} active${openTotal > 1 ? 's' : ''} sur ${list.length} projet${list.length > 1 ? 's' : ''}${critical ? `, dont ${critical} critique${critical > 1 ? 's' : ''}` : ''}.`
        }
        actions={<NewProjectDialog />}
      />
      <div className="px-4 py-6 sm:px-8">
        {projects.isPending ? (
          <div className="grid gap-3">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-20" />
            ))}
          </div>
        ) : projects.isError ? (
          <ErrorState error={projects.error} onRetry={() => projects.refetch()} />
        ) : list.length === 0 ? (
          <EmptyState title="Aucun projet" action={<NewProjectDialog />}>
            Un projet regroupe les failles d’un dépôt. Une fois créé, renseignez son identifiant dans l’app desktop pour y
            envoyer vos scans.
          </EmptyState>
        ) : (
          <ul className="grid divide-y divide-rule overflow-hidden rounded-xl ring-1 ring-rule">
            {list.map((p, i) => (
              <ProjectRow
                key={p.id}
                project={p}
                metrics={metrics[i]?.data}
                history={(scans[i]?.data ?? []).map((s) => s.findings_count).reverse()}
                lastScan={scans[i]?.data?.[0]?.created_at}
              />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

function ProjectRow({
  project,
  metrics,
  history,
  lastScan,
}: {
  project: Project
  metrics?: { grade: string; score: number; open_counts: Record<string, number> }
  history: number[]
  lastScan?: string
}) {
  const open = metrics ? SEVERITIES.reduce((n, s) => n + (metrics.open_counts[s] ?? 0), 0) : null
  return (
    <li className="relative grid grid-cols-[auto_minmax(0,1fr)] items-center gap-x-5 gap-y-3 bg-night px-5 py-4 hover:bg-deck/60 md:grid-cols-[auto_minmax(0,1.3fr)_minmax(0,1fr)_auto_auto]">
      {metrics ? <GradeMark grade={metrics.grade as Grade} score={metrics.score} /> : <Skeleton className="size-11" />}
      <div className="min-w-0">
        <Link
          to="/projects/$projectId"
          params={{ projectId: project.id }}
          className="font-display text-lg font-semibold after:absolute after:inset-0 hover:text-signal"
        >
          {project.name}
        </Link>
        <p className="truncate text-[13px] text-haze">
          {project.repo_url ?? `Projet n° ${project.id}`}
          {lastScan ? `, dernier scan ${timeAgo(lastScan)}` : ', jamais scanné'}
        </p>
      </div>
      <div className="col-span-2 grid gap-1.5 md:col-span-1">
        {metrics ? <SeverityBar counts={metrics.open_counts} /> : <Skeleton className="h-2" />}
        <p className="text-[13px] text-fog tabular">
          {open === null
            ? ' '
            : open === 0
              ? 'Aucune faille active'
              : SEVERITIES.filter((s) => (metrics?.open_counts[s] ?? 0) > 0)
                  .map((s) => `${metrics?.open_counts[s]} ${SEVERITY_META[s].label.toLowerCase()}`)
                  .join(', ')}
        </p>
      </div>
      <Sparkline values={history} label={`Findings par scan pour ${project.name}`} className="hidden md:block" />
      <span className="hidden text-right font-display text-2xl font-semibold tabular md:block" aria-label={`${open ?? 0} failles actives`}>
        {open ?? ''}
      </span>
    </li>
  )
}

function NewProjectDialog() {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const create = useMutation({
    mutationFn: (v: { name: string; repo?: string }) => api.createProject(v.name, v.repo),
    onSuccess: async (p) => {
      await queryClient.invalidateQueries({ queryKey: ['projects'] })
      setOpen(false)
      toast.success(`Projet « ${p.name} » créé (n° ${p.id}).`)
      await navigate({ to: '/projects/$projectId', params: { projectId: p.id } })
    },
  })
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="primary">
          <Plus /> Nouveau projet
        </Button>
      </DialogTrigger>
      <DialogContent title="Nouveau projet" description="Un projet par dépôt. Son numéro sert à relier l’app desktop.">
        <form
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            const f = new FormData(e.currentTarget)
            create.mutate({ name: String(f.get('name')).trim(), repo: String(f.get('repo') ?? '').trim() || undefined })
          }}
        >
          <Field label="Nom" htmlFor="name">
            <Input id="name" name="name" required maxLength={200} placeholder="api-paiements" autoFocus />
          </Field>
          <Field label="Dépôt (facultatif)" htmlFor="repo" error={create.error?.message}>
            <Input id="repo" name="repo" maxLength={500} placeholder="https://github.com/acme/api-paiements" />
          </Field>
          <Button type="submit" variant="primary" disabled={create.isPending} className="justify-self-end">
            {create.isPending ? 'Création…' : 'Créer le projet'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
