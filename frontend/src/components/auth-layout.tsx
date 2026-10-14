import { Link } from '@tanstack/react-router'
import type { ReactNode } from 'react'
import { Logo } from './domain'

/** Mise en page des écrans d'accès : formulaire à gauche, rappel du principe à droite. */
export function AuthLayout({ title, intro, children, footer }: { title: string; intro: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <main id="contenu" className="flex flex-col px-6 py-8 sm:px-10">
        <Link to="/" aria-label="CodeShield, accueil" className="w-fit">
          <Logo />
        </Link>
        <div className="mx-auto grid w-full max-w-sm flex-1 content-center gap-8 py-12">
          <div className="grid gap-2">
            <h1 className="text-3xl font-semibold">{title}</h1>
            <p className="text-fog">{intro}</p>
          </div>
          {children}
          <p className="text-sm text-fog">{footer}</p>
        </div>
      </main>
      <aside className="relative hidden overflow-hidden border-l border-rule bg-deck lg:block" aria-hidden>
        <div className="relative grid h-full content-center gap-6 p-14">
          <div className="max-w-md rounded-xl bg-night/80 p-6 font-mono text-[12.5px] leading-[1.9] ring-1 ring-rule-strong">
            <p className="text-haze">Dernier envoi du desktop</p>
            <p className="mt-3 text-paper">
              <span className="text-fog">file</span>  app/routes/users.py
            </p>
            <p className="text-paper">
              <span className="text-fog">line</span>  10
            </p>
            <p className="text-paper">
              <span className="text-fog">rule</span>  formatted-sql-query
            </p>
            <p className="text-paper">
              <span className="text-fog">cwe</span>   CWE-89
            </p>
          </div>
          <p className="max-w-md font-display text-2xl leading-snug text-paper">
            Voilà tout ce que nous savons de votre code. Le reste n’a jamais quitté votre machine.
          </p>
        </div>
      </aside>
    </div>
  )
}
