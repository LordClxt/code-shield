import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { AuthLayout } from '@/components/auth-layout'
import { Button } from '@/components/ui/button'
import { Field, Input } from '@/components/ui/form'
import { api, ApiError } from '@/lib/api'

export const Route = createFileRoute('/register')({
  head: () => ({ meta: [{ title: 'Créer un compte · CodeShield' }] }),
  component: Register,
})

function Register() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const email = String(form.get('email'))
    const password = String(form.get('password'))
    if (password.length < 8) {
      setError('Le mot de passe doit contenir au moins 8 caractères.')
      return
    }
    setPending(true)
    setError(null)
    try {
      await api.register(email, password)
      await api.login(email, password)
      await navigate({ to: '/dashboard' })
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 400
          ? 'Un compte existe déjà avec cet email. Connectez-vous plutôt.'
          : (err as Error).message,
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthLayout
      title="Créer un compte"
      intro="Ensuite, créez un projet et lancez votre premier scan depuis l’app desktop."
      footer={
        <>
          Déjà inscrit ?{' '}
          <Link to="/login" className="text-signal underline-offset-4 hover:underline">
            Se connecter
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="grid gap-5">
        <Field label="Email professionnel" htmlFor="email">
          <Input id="email" name="email" type="email" autoComplete="email" required autoFocus />
        </Field>
        <Field label="Mot de passe" htmlFor="password" hint="8 caractères minimum." error={error}>
          <Input id="password" name="password" type="password" autoComplete="new-password" minLength={8} required aria-invalid={!!error} />
        </Field>
        <Button type="submit" variant="primary" size="lg" disabled={pending}>
          {pending ? 'Création…' : 'Créer mon compte'}
        </Button>
      </form>
    </AuthLayout>
  )
}
