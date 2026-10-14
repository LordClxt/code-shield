/**
 * Helpers purs pour les graphs façon ref (barres arrondies + sparklines).
 * Miroir de desktop `Charts.kt` (`barFractions`, `sparkPoints`).
 */

/** Hauteurs normalisées 0..1 (max -> 1, vide -> []). */
export function normalizeBars(values: number[]): number[] {
  const max = Math.max(0, ...values)
  if (values.length === 0) return []
  if (max <= 0) return values.map(() => 0)
  return values.map((v) => v / max)
}

export interface Point {
  x: number
  y: number
}

/**
 * Points d'une sparkline normalisés dans [0..w]×[0..h].
 * Une valeur unique -> point centré ; valeurs égales -> ligne médiane.
 */
export function sparklinePoints(values: number[], w: number, h: number): Point[] {
  if (values.length === 0 || w <= 0 || h <= 0) return []
  if (values.length === 1) return [{ x: w / 2, y: h / 2 }]
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min
  return values.map((v, i) => ({
    x: (i / (values.length - 1)) * w,
    y: span > 0 ? h - ((v - min) / span) * h : h / 2,
  }))
}
