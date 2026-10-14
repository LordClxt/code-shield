import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { PageHeader } from '@/components/page-header'
import { Button } from '@/components/ui/button'
import { Checkbox, Field, Input, Label } from '@/components/ui/form'
import { CommandBlock, CopyButton, EmptyState, ErrorState, Skeleton } from '@/components/ui/misc'
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/overlay'
import { api, mcpAddCommand } from '@/lib/api'
import { q } from '@/lib/queries'
import type { ApiKeyCreated, Scope } from '@/lib/types'
import { SCOPES } from '@/lib/types'
import { cn, timeAgo } from '@/lib/utils'

export const Route = createFileRoute('/_app/settings/keys')({
  loader: ({ context }) => context.queryClient.ensureQueryData(q.keys()),
  head: () => ({ meta: [{ title: 'Clés API · CodeShield' }] }),
  component: Keys,
})

const SCOPE_META: Record<Scope, { label: string; hint: string }> = {
  'sync:write': { label: 'Envoyer des scans', hint: 'Pour l’app desktop.' },
  'issues:read': { label: 'Lire les failles', hint: 'Pour Claude Code et les autres agents (MCP).' },
  'issues:write': { label: 'Mettre à jour les failles', hint: 'Permet à un agent de marquer une faille corrigée ou ignorée.' },
}

const PRESETS: { label: string; scopes: Scope[] }[] = [
  { label: 'App desktop', scopes: ['sync:write'] },
  { label: 'Claude Code', scopes: ['issues:read', 'issues:write'] },
]

function Keys() {
  const keys = useQuery(q.keys())
  const queryClient = useQueryClient()
  const revoke = useMutation({
    mutationFn: api.revokeKey,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['keys'] })
      toast.success('Clé révoquée. Elle ne fonctionne plus immédiatement.')
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <>
      <PageHeader
        title="Clés API et Claude Code"
        description="Une clé par usage : l’app desktop envoie les scans, Claude Code lit et met à jour les failles."
        actions={<CreateKeyDialog />}
      />
      <div className="grid gap-10 px-4 py-8 sm:px-8 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <section aria-labelledby="cles" className="grid content-start gap-4">
          <h2 id="cles" className="text-xl font-semibold">Vos clés</h2>
          {keys.isPending ? (
            <Skeleton className="h-40" />
          ) : keys.isError ? (
            <ErrorState error={keys.error} onRetry={() => keys.refetch()} />
          ) : keys.data.length === 0 ? (
            <EmptyState title="Aucune clé" action={<CreateKeyDialog />}>
              Créez une clé pour l’app desktop, puis une autre pour Claude Code. Chaque clé n’a que les droits nécessaires.
            </EmptyState>
          ) : (
            <ul className="divide-y divide-rule rounded-xl ring-1 ring-rule">
              {keys.data.map((k) => (
                <li key={k.id} className={cn('flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-4', k.revoked && 'opacity-55')}>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">
                      {k.name}
                      {k.revoked ? <span className="ml-2 text-[13px] font-normal text-haze">révoquée</span> : null}
                    </p>
                    <p className="text-[13px] text-haze">
                      <span className="font-mono">{k.prefix}…</span>
                      {`, ${k.scopes.map((s) => SCOPE_META[s].label.toLowerCase()).join(', ')}`}
                      {k.project_id ? `, projet n° ${k.project_id} uniquement` : ''}
                    </p>
                    <p className="text-[13px] text-haze">
                      Créée {timeAgo(k.created_at)}, {k.last_used_at ? `utilisée ${timeAgo(k.last_used_at)}` : 'jamais utilisée'}
                    </p>
                  </div>
                  {!k.revoked ? (
                    <Button
                      size="sm"
                      variant="danger"
                      disabled={revoke.isPending}
                      onClick={() => {
                        if (window.confirm(`Révoquer « ${k.name} » ? Les outils qui l’utilisent perdront l’accès.`)) revoke.mutate(k.id)
                      }}
                    >
                      Révoquer
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="mcp" className="grid content-start gap-4">
          <h2 id="mcp" className="text-xl font-semibold">Brancher Claude Code</h2>
          <ol className="grid list-decimal gap-3 pl-5 text-[14.5px] text-fog marker:text-haze">
            <li>Créez une clé avec le modèle « Claude Code ».</li>
            <li>Dans le terminal, ajoutez CodeShield comme serveur MCP (la commande est générée avec votre clé à la création).</li>
            <li>
              Dans votre dépôt, demandez par exemple :{' '}
              <span className="text-paper">« corrige les failles critiques du projet 3 »</span>.
            </li>
            <li>Relancez un scan depuis l’app desktop : les failles corrigées passent en résolues.</li>
          </ol>
          <CommandBlock command={mcpAddCommand('csk_…')} />
        </section>
      </div>
    </>
  )
}

function CreateKeyDialog() {
  const [open, setOpen] = useState(false)
  const [scopes, setScopes] = useState<Scope[]>(['sync:write'])
  const [projectId, setProjectId] = useState<string>('')
  const [created, setCreated] = useState<ApiKeyCreated | null>(null)
  const projects = useQuery({ ...q.projects(), enabled: open })
  const queryClient = useQueryClient()
  const create = useMutation({
    mutationFn: (name: string) => api.createKey({ name, scopes, project_id: projectId ? Number(projectId) : null }),
    onSuccess: async (k) => {
      setCreated(k)
      await queryClient.invalidateQueries({ queryKey: ['keys'] })
    },
  })

  const reset = (o: boolean) => {
    setOpen(o)
    if (!o) {
      setCreated(null)
      setScopes(['sync:write'])
      setProjectId('')
      create.reset()
    }
  }

  return (
    <Dialog open={open} onOpenChange={reset}>
      <DialogTrigger asChild>
        <Button variant="primary">
          <Plus /> Nouvelle clé
        </Button>
      </DialogTrigger>
      {created ? (
        <DialogContent title="Clé créée" description="Copiez-la maintenant : elle ne sera plus jamais affichée.">
          <div className="grid gap-4">
            <div className="flex items-center gap-2 rounded-lg bg-night px-3 py-2.5 ring-1 ring-signal/40">
              <code className="min-w-0 flex-1 font-mono text-[13px] break-all">{created.plain_key}</code>
              <CopyButton value={created.plain_key} />
            </div>
            {created.scopes.includes('issues:read') ? (
              <div className="grid gap-2">
                <p className="text-sm text-fog">Pour Claude Code, lancez :</p>
                <CommandBlock command={mcpAddCommand(created.plain_key)} />
              </div>
            ) : (
              <p className="text-sm text-fog">
                Dans l’app desktop, ouvrez Synchronisation et collez cette clé avec l’adresse du serveur.
              </p>
            )}
            <Button onClick={() => reset(false)} className="justify-self-end">
              J’ai copié la clé
            </Button>
          </div>
        </DialogContent>
      ) : (
        <DialogContent title="Nouvelle clé API" description="Donnez à chaque outil uniquement les droits dont il a besoin.">
          <form
            className="grid gap-5"
            onSubmit={(e) => {
              e.preventDefault()
              create.mutate(String(new FormData(e.currentTarget).get('name')).trim())
            }}
          >
            <Field label="Nom" htmlFor="key-name">
              <Input id="key-name" name="name" required maxLength={200} placeholder="MacBook de Lina, Claude Code…" autoFocus />
            </Field>
            <fieldset className="grid gap-3">
              <legend className="mb-2 text-[13px] font-medium text-fog">Droits</legend>
              <div className="flex gap-2">
                {PRESETS.map((p) => (
                  <Button
                    key={p.label}
                    type="button"
                    size="sm"
                    variant={p.scopes.join() === scopes.join() ? 'secondary' : 'ghost'}
                    onClick={() => setScopes(p.scopes)}
                  >
                    {p.label}
                  </Button>
                ))}
              </div>
              {SCOPES.map((s) => (
                <div key={s} className="flex items-start gap-3">
                  <Checkbox
                    id={`scope-${s}`}
                    checked={scopes.includes(s)}
                    onCheckedChange={(c) => setScopes((cur) => (c ? [...new Set([...cur, s])] : cur.filter((x) => x !== s)))}
                    className="mt-0.5"
                  />
                  <Label htmlFor={`scope-${s}`} className="grid gap-0.5 text-paper">
                    {SCOPE_META[s].label}
                    <span className="font-normal text-haze">{SCOPE_META[s].hint}</span>
                  </Label>
                </div>
              ))}
            </fieldset>
            <Field label="Projet" htmlFor="key-project" hint="Restreindre la clé à un seul projet limite les dégâts si elle fuit.">
              <select
                id="key-project"
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                className="h-10 rounded-md bg-night px-3 text-sm ring-1 ring-rule-strong"
              >
                <option value="">Tous mes projets</option>
                {projects.data?.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </Field>
            {create.error ? <p role="alert" className="text-sm text-danger">{create.error.message}</p> : null}
            <Button type="submit" variant="primary" className="justify-self-end" disabled={scopes.length === 0 || create.isPending}>
              {create.isPending ? 'Création…' : 'Créer la clé'}
            </Button>
          </form>
        </DialogContent>
      )}
    </Dialog>
  )
}
