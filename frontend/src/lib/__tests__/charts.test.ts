import { describe, expect, it } from 'vitest'
import { normalizeBars, sparklinePoints } from '../charts'

describe('charts (miroir desktop Charts.kt)', () => {
  it('normalizeBars normalise par le max', () => {
    expect(normalizeBars([1, 2, 4])).toEqual([0.25, 0.5, 1])
  })

  it('normalizeBars gère vide et zéros', () => {
    expect(normalizeBars([])).toEqual([])
    expect(normalizeBars([0, 0])).toEqual([0, 0])
  })

  it('sparklinePoints gère vide et valeur unique', () => {
    expect(sparklinePoints([], 100, 40)).toEqual([])
    expect(sparklinePoints([5], 100, 40)).toEqual([{ x: 50, y: 20 }])
  })

  it('sparklinePoints reste dans les bornes', () => {
    const pts = sparklinePoints([3, 1, 4, 1, 5], 120, 50)
    expect(pts).toHaveLength(5)
    expect(pts[0]?.x).toBe(0)
    expect(pts[4]?.x).toBe(120)
    for (const p of pts) {
      expect(p.x).toBeGreaterThanOrEqual(0)
      expect(p.x).toBeLessThanOrEqual(120)
      expect(p.y).toBeGreaterThanOrEqual(0)
      expect(p.y).toBeLessThanOrEqual(50)
    }
  })

  it('valeurs égales -> ligne médiane (comme desktop)', () => {
    for (const p of sparklinePoints([2, 2, 2], 90, 30)) {
      expect(p.y).toBe(15)
    }
  })
})
