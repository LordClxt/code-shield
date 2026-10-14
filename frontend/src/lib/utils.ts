import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

const rtf = new Intl.RelativeTimeFormat('fr', { numeric: 'auto' })

/** « il y a 3 heures », « hier »… */
export function timeAgo(iso: string, now = Date.now()): string {
  const diff = (new Date(iso).getTime() - now) / 1000
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000], ['month', 2_592_000], ['week', 604_800], ['day', 86_400], ['hour', 3_600], ['minute', 60],
  ]
  for (const [unit, secs] of units) {
    if (Math.abs(diff) >= secs) return rtf.format(Math.round(diff / secs), unit)
  }
  return 'à l’instant'
}

/** `app/db.py:42` ou `app/db.py:42-44`. */
export function location(v: { file_path: string; line_number: number; end_line?: number | null }): string {
  return v.end_line && v.end_line !== v.line_number
    ? `${v.file_path}:${v.line_number}-${v.end_line}`
    : `${v.file_path}:${v.line_number}`
}
