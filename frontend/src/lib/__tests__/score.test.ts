import { describe, expect, it } from 'vitest'
import { computeScore } from '../score'

describe('computeScore (miroir backend-go score.go)', () => {
  it('projet sain => 100/A', () => {
    expect(computeScore({})).toEqual({ score: 100, grade: 'A' })
    expect(computeScore({ CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 })).toEqual({
      score: 100,
      grade: 'A',
    })
  })

  it('1 CRITICAL (-10) => 90/A', () => {
    expect(computeScore({ CRITICAL: 1 })).toEqual({ score: 90, grade: 'A' })
  })

  it('seuils de grades A/B/C/D/F', () => {
    expect(computeScore({ HIGH: 2, MEDIUM: 1 }).grade).toBe('B') // 88
    expect(computeScore({ HIGH: 8 })).toEqual({ score: 60, grade: 'C' })
    expect(computeScore({ HIGH: 12 })).toEqual({ score: 40, grade: 'D' })
    expect(computeScore({ CRITICAL: 20 })).toEqual({ score: 0, grade: 'F' })
  })

  it('score plancher à 0, jamais négatif', () => {
    expect(computeScore({ CRITICAL: 100 }).score).toBe(0)
  })

  it('1 HIGH via sync e2e => 95/A (cohérent avec le test backend)', () => {
    expect(computeScore({ HIGH: 1 })).toEqual({ score: 95, grade: 'A' })
  })
})
