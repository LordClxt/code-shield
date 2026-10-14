import { QueryClientProvider } from '@tanstack/react-query'
import { createRootRouteWithContext, HeadContent, Link, Outlet, Scripts } from '@tanstack/react-router'
import type { ReactNode } from 'react'
import { Toaster } from 'sonner'
import type { RouterContext } from '@/router'
import appCss from '@/styles.css?url'

export const Route = createRootRouteWithContext<RouterContext>()({
  head: () => ({
    meta: [
      { charSet: 'utf-8' },
      { name: 'viewport', content: 'width=device-width, initial-scale=1' },
      { title: 'CodeShield — audit de sécurité du code, sans que le code quitte votre machine' },
      {
        name: 'description',
        content:
          'Téléchargez l’app desktop CodeShield : scannez votre code en local (SAST, secrets, dépendances, IaC) et laissez Claude Code corriger les failles en un clic. macOS, Windows et Linux.',
      },
      { name: 'theme-color', content: '#09090b' },
    ],
    links: [
      { rel: 'stylesheet', href: appCss },
      { rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' },
    ],
  }),
  shellComponent: RootDocument,
  component: RootComponent,
  notFoundComponent: NotFound,
})

function RootComponent() {
  const { queryClient } = Route.useRouteContext()
  return (
    <QueryClientProvider client={queryClient}>
      <Outlet />
      <Toaster
        theme="dark"
        position="bottom-right"
        toastOptions={{ className: '!bg-deck-2 !text-paper !ring-1 !ring-rule-strong !border-0 !font-sans' }}
      />
    </QueryClientProvider>
  )
}

function RootDocument({ children }: { children: ReactNode }) {
  return (
    <html lang="fr">
      <head>
        <HeadContent />
      </head>
      <body>
        <a href="#contenu" className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-md focus:bg-signal focus:px-3 focus:py-2 focus:text-signal-ink">
          Aller au contenu
        </a>
        {children}
        <Scripts />
      </body>
    </html>
  )
}

function NotFound() {
  return (
    <main id="contenu" className="mx-auto grid min-h-dvh max-w-xl content-center gap-4 px-6">
      <p className="font-mono text-sm text-haze">404</p>
      <h1 className="text-4xl font-semibold">Cette page n’existe pas.</h1>
      <p className="text-fog">Le lien est peut-être ancien, ou le projet a été supprimé.</p>
      <Link to="/" className="text-signal underline-offset-4 hover:underline">
        Revenir à l’accueil
      </Link>
    </main>
  )
}
