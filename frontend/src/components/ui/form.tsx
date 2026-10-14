import { Checkbox as CheckboxPrimitive, Label as LabelPrimitive } from 'radix-ui'
import { Check } from 'lucide-react'
import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

export function Input({ className, ...props }: ComponentProps<'input'>) {
  return (
    <input
      className={cn(
        'h-10 w-full rounded-md bg-night px-3 text-sm text-paper ring-1 ring-rule-strong placeholder:text-haze',
        'transition-shadow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-signal',
        'aria-invalid:ring-danger',
        className,
      )}
      {...props}
    />
  )
}

export function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return (
    <textarea
      className={cn(
        'min-h-20 w-full rounded-md bg-night px-3 py-2 text-sm text-paper ring-1 ring-rule-strong placeholder:text-haze',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-signal',
        className,
      )}
      {...props}
    />
  )
}

export function Label({ className, ...props }: ComponentProps<typeof LabelPrimitive.Root>) {
  return <LabelPrimitive.Root className={cn('text-[13px] font-medium text-fog', className)} {...props} />
}

export function Checkbox({ className, ...props }: ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      className={cn(
        'grid size-4 shrink-0 place-items-center rounded-sm ring-1 ring-rule-strong data-[state=checked]:bg-signal data-[state=checked]:ring-signal',
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator>
        <Check className="size-3 text-signal-ink" strokeWidth={3} />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  )
}

/** Champ complet : libellé, contrôle, aide ou erreur. */
export function Field({
  label,
  htmlFor,
  hint,
  error,
  children,
}: {
  label: string
  htmlFor: string
  hint?: string
  error?: string | null
  children: React.ReactNode
}) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {error ? (
        <p role="alert" className="text-[13px] text-danger">
          {error}
        </p>
      ) : hint ? (
        <p className="text-[13px] text-haze">{hint}</p>
      ) : null}
    </div>
  )
}
