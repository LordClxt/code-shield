import { QueryClient } from '@tanstack/react-query'
import { createRouter } from '@tanstack/react-router'
import { setUnauthorizedHandler } from './lib/api'
import { routeTree } from './routeTree.gen'

export interface RouterContext {
  queryClient: QueryClient
}

export function getRouter() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (count, err) => count < 2 && !(err instanceof Error && 'status' in err && (err as { status: number }).status < 500),
      },
    },
  })
  const router = createRouter({
    routeTree,
    context: { queryClient },
    scrollRestoration: true,
    defaultPreload: 'intent',
    defaultPreloadStaleTime: 0,
  })
  // Session expirée : retour à la connexion en gardant la page demandée.
  setUnauthorizedHandler(() => {
    queryClient.clear()
    void router.navigate({ to: '/login', search: { redirect: router.state.location.href } })
  })
  return router
}

declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof getRouter>
  }
}
