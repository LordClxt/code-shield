/**
 * Client HTTP typé vers l'API CodeShield (backend Go) + garde-fous
 * « métadonnées uniquement ». Base URL : VITE_API_URL (défaut http://localhost:8000).
 */
import type {
  ApiKey,
  ApiKeyCreate,
  ApiKeyCreated,
  Finding,
  IssueDetail,
  IssuePage,
  Project,
  ProjectMetrics,
  Scan,
  SyncResult,
  TimelinePoint,
  User,
  Vulnerability,
  VulnStatus,
} from './types'

export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8000'

const TOKEN_KEY = 'codeshield.token'

export function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return window.localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (typeof window === 'undefined') return
  if (token) window.localStorage.setItem(TOKEN_KEY, token)
  else window.localStorage.removeItem(TOKEN_KEY)
}

const FORBIDDEN_PAYLOAD_KEYS = ['source_code', 'snippet', 'code', 'source', 'raw_code']

/** Garde-fou : refuse tout payload contenant du code source brut. */
export function assertNoSourceCode(value: unknown, path = '$'): void {
  if (Array.isArray(value)) {
    value.forEach((v, i) => assertNoSourceCode(v, `${path}[${i}]`))
    return
  }
  if (value !== null && typeof value === 'object') {
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (FORBIDDEN_PAYLOAD_KEYS.includes(k)) {
        throw new Error(`Payload refusé (${path}.${k}) : le code source brut est interdit`)
      }
      assertNoSourceCode(v, `${path}.${k}`)
    }
  }
}

/** Erreur HTTP avec le `detail` renvoyé par l'API, lisible dans l'UI. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

/** Appelé sur 401 (session expirée) : branché par le routeur pour rediriger vers /login. */
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

function detailOf(body: unknown, fallback: string): string {
  if (body && typeof body === 'object' && 'detail' in body) {
    const d = (body as { detail: unknown }).detail
    if (typeof d === 'string') return d
    if (Array.isArray(d)) return d.map((e) => (e as { msg?: string }).msg ?? '').filter(Boolean).join(' · ')
  }
  return fallback
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers ?? {}),
    },
  })
  if (res.status === 401 && token && !path.startsWith('/api/v1/auth/login')) {
    setToken(null)
    onUnauthorized()
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(res.status, detailOf(body, `Erreur ${res.status}`))
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

function qs(params: Record<string, string | number | undefined | null>): string {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '') q.set(k, String(v))
  const s = q.toString()
  return s ? `?${s}` : ''
}

export const api = {
  register: (email: string, password: string) =>
    request<User>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }),
  login: async (email: string, password: string) => {
    const data = await request<{ access_token: string }>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    setToken(data.access_token)
    return data
  },
  logout: () => setToken(null),
  me: () => request<User>('/api/v1/auth/me'),

  listProjects: () => request<Project[]>('/api/v1/projects'),
  getProject: (id: number) => request<Project>(`/api/v1/projects/${id}`),
  createProject: (name: string, repo_url?: string) =>
    request<Project>('/api/v1/projects', {
      method: 'POST',
      body: JSON.stringify({ name, repo_url: repo_url || null }),
    }),
  deleteProject: (id: number) => request<void>(`/api/v1/projects/${id}`, { method: 'DELETE' }),
  getVulnerabilities: (projectId: number, params: { status?: string; severity?: string } = {}) =>
    request<Vulnerability[]>(`/api/v1/projects/${projectId}/vulnerabilities${qs(params)}`),
  getScans: (projectId: number) => request<Scan[]>(`/api/v1/projects/${projectId}/scans`),
  getMetrics: (projectId: number) => request<ProjectMetrics>(`/api/v1/projects/${projectId}/metrics`),
  getTimeline: (projectId: number, days = 30) =>
    request<TimelinePoint[]>(`/api/v1/projects/${projectId}/timeline${qs({ days })}`),

  listIssues: (params: { project_id?: number; status?: string; severity?: string; cursor?: string; limit?: number }) =>
    request<IssuePage>(`/api/v1/issues${qs(params)}`),
  getIssue: (id: number) => request<IssueDetail>(`/api/v1/issues/${id}`),
  updateIssue: (id: number, status: VulnStatus, note?: string) =>
    request<IssueDetail>(`/api/v1/issues/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ status, note: note || null }),
    }),

  /** Soumission Desktop (métadonnées uniquement — jamais de code source). */
  submitSync: (projectId: number, findings: Finding[]) => {
    assertNoSourceCode({ project_id: projectId, findings })
    return request<SyncResult>('/api/v1/sync/submit', {
      method: 'POST',
      body: JSON.stringify({ project_id: projectId, findings }),
    })
  },

  listKeys: () => request<ApiKey[]>('/api/v1/keys'),
  createKey: (body: ApiKeyCreate) =>
    request<ApiKeyCreated>('/api/v1/keys', { method: 'POST', body: JSON.stringify(body) }),
  revokeKey: (id: number) => request<void>(`/api/v1/keys/${id}`, { method: 'DELETE' }),
}

/** Commande `claude mcp add` pré-remplie pour brancher Claude Code sur CodeShield. */
export function mcpAddCommand(key: string, apiUrl = API_URL): string {
  return `claude mcp add --transport http codeshield ${apiUrl.replace(/\/$/, '')}/mcp --header "Authorization: Bearer ${key}"`
}
