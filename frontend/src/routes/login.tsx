import { useQueryClient } from '@tanstack/react-query'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { AuthLayout } from '@/components/auth-layout'
import { Button } from '@/components/ui/button'
import { Field, Input } from '@/components/ui/form'
import { api, ApiError } from '@/lib/api'

export const Route = createFileRoute('/login')({
  validateSearch: (s: Record<string, unknown>): { redirect?: string } => ({
    redirect: typeof s.redirect === 'string' && s.redirect.startsWith('/') ? s.redirect : undefined,
  }),
  head: () => ({ meta: [{ title: 'Connexion · CodeShield' }] }),
  component: Login,
})

function Login() {
  const { redirect } = Route.useSearch()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setPending(true)
    setError(null)
    try {
      await api.login(String(form.get('email')), String(form.get('password')))
      queryClient.clear()
      await navigate({ to: redirect ?? '/dashboard' })
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? 'Email ou mot de passe incorrect.' : (err as Error).message)
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthLayout
      title="Connexion"
      intro="Retrouvez les failles de vos projets et leur suivi."
      footer={
        <>
          Pas encore de compte ?{' '}
          <Link to="/register" className="text-signal underline-offset-4 hover:underline">
            Créer un compte
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="grid gap-5" noValidate={false}>
        <Field label="Email" htmlFor="email">
          <Input id="email" name="email" type="email" autoComplete="email" required autoFocus />
        </Field>
        <Field label="Mot de passe" htmlFor="password" error={error}>
          <Input id="password" name="password" type="password" autoComplete="current-password" required aria-invalid={!!error} />
        </Field>
        <Button type="submit" variant="primary" size="lg" disabled={pending}>
          {pending ? 'Connexion…' : 'Se connecter'}
        </Button>
      </form>
    </AuthLayout>
  )
}
