import type { ComponentProps, ReactNode } from 'react'
import { GradeMark } from './domain'

/** En-tête de page : note éventuelle, titre, description et actions alignés. */
export function PageHeader({
  title,
  description,
  actions,
  grade,
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  grade?: ComponentProps<typeof GradeMark>
}) {
  return (
    <header className="flex flex-wrap items-center gap-4 border-b border-rule px-4 py-6 sm:px-8">
      {grade ? <GradeMark {...grade} size="lg" /> : null}
      <div className="min-w-0 flex-1">
        <h1 className="truncate text-[28px] leading-tight font-semibold">{title}</h1>
        {description ? <div className="mt-1 text-sm text-fog">{description}</div> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  )
}
