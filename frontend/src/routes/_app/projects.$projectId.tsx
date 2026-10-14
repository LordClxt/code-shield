import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFileRoute, Link, Outlet, notFound, useNavigate } from '@tanstack/react-router'
import { MoreHorizontal, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { PageHeader } from '@/components/page-header'
import { Button } from '@/components/ui/button'
import { ErrorState, Skeleton } from '@/components/ui/misc'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/overlay'
import { api, ApiError } from '@/lib/api'
import { q } from '@/lib/queries'
import type { Grade } from '@/lib/score'

export const Route = createFileRoute('/_app/projects/$projectId')({
  params: {
    parse: (p) => ({ projectId: Number(p.projectId) }),
    stringify: (p) => ({ projectId: String(p.projectId) }),
  },
  loader: async ({ context, params }) => {
    if (!Number.isInteger(params.projectId) || params.projectId <= 0) throw notFound()
    try {
      return await context.queryClient.ensureQueryData(q.project(params.projectId))
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) throw notFound()
      throw e
    }
  },
  head: ({ loaderData }) => ({ meta: [{ title: `${loaderData?.name ?? 'Projet'} · CodeShield` }] }),
  component: ProjectLayout,
})

const TABS = [
  { to: '/projects/$projectId', label: 'Vue d’ensemble', exact: true },
  { to: '/projects/$projectId/vulnerabilities', label: 'Failles', exact: false },
  { to: '/projects/$projectId/metrics', label: 'Évolution', exact: false },
] as const

function ProjectLayout() {
  const { projectId } = Route.useParams()
  const project = useQuery(q.project(projectId))
  const metrics = useQuery(q.metrics(projectId))
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const remove = useMutation({
    mutationFn: () => api.deleteProject(projectId),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['projects', projectId] })
      await queryClient.invalidateQueries({ queryKey: ['projects'] })
      toast.success('Projet supprimé.')
      await navigate({ to: '/dashboard' })
    },
    onError: (e) => toast.error(e.message),
  })

  if (project.isError) return <div className="p-8"><ErrorState error={project.error} onRetry={() => project.refetch()} /></div>

  return (
    <>
      <PageHeader
        grade={metrics.data ? { grade: metrics.data.grade as Grade, score: metrics.data.score } : undefined}
        title={project.data?.name ?? <Skeleton className="h-8 w-48" />}
        description={
          <>
            {project.data?.repo_url ? (
              <a href={project.data.repo_url} className="hover:text-paper hover:underline" rel="noreferrer" target="_blank">
                {project.data.repo_url}
              </a>
            ) : (
              'Aucun dépôt renseigné'
            )}
            <span className="text-haze">{`, projet n° ${projectId} pour l’app desktop`}</span>
          </>
        }
        actions={
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" aria-label="Actions du projet">
                <MoreHorizontal />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                className="text-danger"
                onSelect={() => {
                  if (window.confirm(`Supprimer « ${project.data?.name} » et tout son historique ? Cette action est définitive.`)) remove.mutate()
                }}
              >
                <Trash2 /> Supprimer le projet
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        }
      />
      <nav className="flex gap-1 overflow-x-auto border-b border-rule px-4 sm:px-8" aria-label="Sections du projet">
        {TABS.map((t) => (
          <Link
            key={t.to}
            to={t.to}
            params={{ projectId }}
            activeOptions={{ exact: t.exact, includeSearch: false }}
            className="-mb-px border-b-2 border-transparent px-3 py-3 text-sm whitespace-nowrap text-fog hover:text-paper"
            activeProps={{ className: '!border-signal !text-paper' }}
          >
            {t.label}
          </Link>
        ))}
      </nav>
      <Outlet />
    </>
  )
}
