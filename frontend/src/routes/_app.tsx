import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createFileRoute, Link, Outlet, redirect, useNavigate } from '@tanstack/react-router'
import { KeyRound, LayoutGrid, LogOut, Menu, X } from 'lucide-react'
import { useState } from 'react'
import { Logo } from '@/components/domain'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/overlay'
import { api, getToken } from '@/lib/api'
import { q } from '@/lib/queries'
import { cn } from '@/lib/utils'

/**
 * Espace connecté. Rendu côté client (`ssr: false`) : le jeton est en
 * localStorage, invisible du serveur. La garde redirige vers /login.
 */
export const Route = createFileRoute('/_app')({
  ssr: false,
  beforeLoad: ({ location }) => {
    if (!getToken()) throw redirect({ to: '/login', search: { redirect: location.href } })
  },
  component: AppShell,
})

function AppShell() {
  const [open, setOpen] = useState(false)
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[15.5rem_minmax(0,1fr)]">
      {/* Barre mobile */}
      <div className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-rule bg-night/90 px-4 backdrop-blur lg:hidden">
        <Button variant="ghost" size="icon" onClick={() => setOpen(true)} aria-label="Ouvrir la navigation">
          <Menu />
        </Button>
        <Link to="/dashboard">
          <Logo />
        </Link>
      </div>
      {open ? <div className="fixed inset-0 z-40 bg-night/70 lg:hidden" onClick={() => setOpen(false)} aria-hidden /> : null}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-rule bg-deck transition-transform lg:sticky lg:top-0 lg:h-dvh lg:w-auto lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-16 items-center px-5">
          <Link to="/dashboard" onClick={() => setOpen(false)}>
            <Logo />
          </Link>
          <Button variant="ghost" size="icon" className="ml-auto lg:hidden" onClick={() => setOpen(false)} aria-label="Fermer la navigation">
            <X />
          </Button>
        </div>
        <Sidebar onNavigate={() => setOpen(false)} />
      </aside>
      <main id="contenu" className="min-w-0">
        <Outlet />
      </main>
    </div>
  )
}

function NavLink({ to, children, onClick }: { to: '/dashboard' | '/settings/keys'; children: React.ReactNode; onClick: () => void }) {
  return (
    <Link
      to={to}
      onClick={onClick}
      className="flex h-9 items-center gap-2.5 rounded-md px-3 text-sm text-fog hover:bg-deck-2 hover:text-paper [&_svg]:size-4"
      activeProps={{ className: 'bg-deck-2 !text-paper' }}
    >
      {children}
    </Link>
  )
}

function Sidebar({ onNavigate }: { onNavigate: () => void }) {
  const { data: projects } = useQuery(q.projects())
  const { data: me } = useQuery(q.me())
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return (
    <nav className="flex min-h-0 flex-1 flex-col gap-6 px-3 pb-4" aria-label="Navigation de l’application">
      <div className="grid gap-0.5">
        <NavLink to="/dashboard" onClick={onNavigate}>
          <LayoutGrid /> Projets
        </NavLink>
        <NavLink to="/settings/keys" onClick={onNavigate}>
          <KeyRound /> Clés API et Claude Code
        </NavLink>
      </div>

      <div className="grid min-h-0 gap-1 overflow-y-auto">
        <p className="px-3 text-[12.5px] text-haze">Vos projets</p>
        {projects?.length ? (
          projects.map((p) => (
            <Link
              key={p.id}
              to="/projects/$projectId"
              params={{ projectId: p.id }}
              onClick={onNavigate}
              className="flex h-8 items-center gap-2 truncate rounded-md px-3 text-[13.5px] text-fog hover:bg-deck-2 hover:text-paper"
              activeProps={{ className: 'bg-deck-2 !text-paper' }}
            >
              <span className="truncate">{p.name}</span>
            </Link>
          ))
        ) : (
          <p className="px-3 text-[13px] text-haze">Aucun projet pour l’instant.</p>
        )}
      </div>

      <div className="mt-auto">
        <DropdownMenu>
          <DropdownMenuTrigger className="flex w-full items-center gap-2.5 rounded-md px-3 py-2 text-left text-sm hover:bg-deck-2">
            <span className="grid size-7 place-items-center rounded-full bg-rule text-[12px] font-semibold uppercase">
              {me?.email.slice(0, 1) ?? '·'}
            </span>
            <span className="min-w-0 flex-1 truncate text-fog">{me?.email ?? 'Compte'}</span>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" side="top">
            <DropdownMenuLabel>{me?.email}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onSelect={() => {
                api.logout()
                queryClient.clear()
                void navigate({ to: '/' })
              }}
            >
              <LogOut /> Se déconnecter
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </nav>
  )
}
