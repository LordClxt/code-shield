import { Tabs as TabsPrimitive } from 'radix-ui'
import { Check, Copy } from 'lucide-react'
import { useEffect, useState, type ComponentProps, type ReactNode } from 'react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import { Button } from './button'

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-pulse rounded-md bg-deck-2', className)} />
}

export const Tabs = TabsPrimitive.Root
export const TabsContent = TabsPrimitive.Content

export function TabsList({ className, ...props }: ComponentProps<typeof TabsPrimitive.List>) {
  return <TabsPrimitive.List className={cn('inline-flex gap-1 rounded-lg bg-night p-1 ring-1 ring-rule', className)} {...props} />
}

export function TabsTrigger({ className, ...props }: ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        'rounded-md px-3 py-1.5 text-[13px] font-medium text-fog transition-colors hover:text-paper data-[state=active]:bg-deck-2 data-[state=active]:text-paper',
        className,
      )}
      {...props}
    />
  )
}

/** Bouton « copier » avec confirmation (texte + coche). */
export function CopyButton({ value, label = 'Copier', className }: { value: string; label?: string; className?: string }) {
  const [copied, setCopied] = useState(false)
  useEffect(() => {
    if (!copied) return
    const t = setTimeout(() => setCopied(false), 1600)
    return () => clearTimeout(t)
  }, [copied])
  return (
    <Button
      type="button"
      size="sm"
      variant="ghost"
      className={className}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value)
          setCopied(true)
        } catch {
          toast.error('Copie impossible : autorisez l’accès au presse-papiers.')
        }
      }}
    >
      {copied ? <Check /> : <Copy />}
      {copied ? 'Copié' : label}
    </Button>
  )
}

/** Bloc de commande copiable (terminal). */
export function CommandBlock({ command, className }: { command: string; className?: string }) {
  return (
    <div className={cn('flex items-start gap-2 rounded-lg bg-night p-3 ring-1 ring-rule', className)}>
      <code className="min-w-0 flex-1 font-mono text-[12.5px] leading-relaxed break-all text-paper">
        <span className="text-haze select-none">$ </span>
        {command}
      </code>
      <CopyButton value={command} className="-my-1" />
    </div>
  )
}

/** État vide : ce qui manque et l'action pour avancer. */
export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="grid justify-items-start gap-3 rounded-xl border border-dashed border-rule-strong p-8">
      <h3 className="text-lg font-semibold">{title}</h3>
      {children ? <div className="max-w-prose text-sm text-fog">{children}</div> : null}
      {action}
    </div>
  )
}

/** Message d'erreur de requête, avec relance. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Erreur inconnue'
  return (
    <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg bg-danger/10 px-4 py-3 text-sm ring-1 ring-danger/30">
      <span className="flex-1 text-paper">Chargement impossible : {message}</span>
      {onRetry ? (
        <Button size="sm" variant="ghost" onClick={onRetry}>
          Réessayer
        </Button>
      ) : null}
    </div>
  )
}
