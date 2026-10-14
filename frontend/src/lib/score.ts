import { SEVERITY_WEIGHTS } from './severity'
import type { Severity } from './types'

export type Grade = 'A' | 'B' | 'C' | 'D' | 'F'

export interface ScoreResult {
  score: number
  grade: Grade
}

/** Arrondi bancaire (moitié vers le pair), comme round() Python et math.RoundToEven en Go. */
function roundHalfEven(x: number): number {
  const r = Math.round(x)
  return Math.abs(x % 1) === 0.5 && r % 2 !== 0 ? r - 1 : r
}

/**
 * Score 0..100 + grade A..F. Miroir exact de backend-go/internal/service/score.go :
 * score = max(0, 100 - round(pénalité pondérée)), seuils 90/75/60/40.
 */
export function computeScore(openCounts: Partial<Record<Severity, number>>): ScoreResult {
  let penalty = 0
  for (const [sev, weight] of Object.entries(SEVERITY_WEIGHTS) as [Severity, number][]) {
    penalty += weight * (openCounts[sev] ?? 0)
  }
  const score = Math.max(0, 100 - roundHalfEven(penalty))
  const grade: Grade = score >= 90 ? 'A' : score >= 75 ? 'B' : score >= 60 ? 'C' : score >= 40 ? 'D' : 'F'
  return { score, grade }
}

/** Couleur et libellé de grade (le libellé accompagne toujours la couleur). */
export const GRADE_META: Record<Grade, { label: string; color: string }> = {
  A: { label: 'Saine', color: '#2FA67B' },
  B: { label: 'Correcte', color: '#7FA63A' },
  C: { label: 'À surveiller', color: '#A0991F' },
  D: { label: 'Dégradée', color: '#BF5410' },
  F: { label: 'Critique', color: '#D33A80' },
}
