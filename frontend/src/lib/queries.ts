import { queryOptions } from '@tanstack/react-query'
import { api } from './api'

/** Clés et fonctions de requête partagées (loaders + composants). */
export const q = {
  me: () => queryOptions({ queryKey: ['me'], queryFn: api.me, staleTime: 5 * 60_000 }),
  projects: () => queryOptions({ queryKey: ['projects'], queryFn: api.listProjects }),
  project: (id: number) => queryOptions({ queryKey: ['projects', id], queryFn: () => api.getProject(id) }),
  metrics: (id: number) => queryOptions({ queryKey: ['projects', id, 'metrics'], queryFn: () => api.getMetrics(id) }),
  scans: (id: number) => queryOptions({ queryKey: ['projects', id, 'scans'], queryFn: () => api.getScans(id) }),
  vulns: (id: number) => queryOptions({ queryKey: ['projects', id, 'vulns'], queryFn: () => api.getVulnerabilities(id) }),
  timeline: (id: number, days: number) =>
    queryOptions({ queryKey: ['projects', id, 'timeline', days], queryFn: () => api.getTimeline(id, days) }),
  issue: (id: number) => queryOptions({ queryKey: ['issues', id], queryFn: () => api.getIssue(id) }),
  keys: () => queryOptions({ queryKey: ['keys'], queryFn: api.listKeys }),
}
