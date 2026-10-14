import { describe, expect, it } from 'vitest'
import { SEVERITY_ORDER, SEVERITY_WEIGHTS, compareSeverity, isSeverity } from '../severity'

describe('severity', () => {
  it('expose exactement les 4 sévérités du contrat (aligné backend/desktop)', () => {
    expect(Object.keys(SEVERITY_WEIGHTS).sort()).toEqual(['CRITICAL', 'HIGH', 'LOW', 'MEDIUM'])
  })

  it('pondérations alignées avec le backend', () => {
    expect(SEVERITY_WEIGHTS).toEqual({ CRITICAL: 10, HIGH: 5, MEDIUM: 2, LOW: 0.5 })
  })

  it('isSeverity valide strictement', () => {
    expect(isSeverity('CRITICAL')).toBe(true)
    expect(isSeverity('critical')).toBe(false)
    expect(isSeverity('INFO')).toBe(false)
    expect(isSeverity(undefined)).toBe(false)
  })

  it('compareSeverity ordonne CRITICAL > HIGH > MEDIUM > LOW', () => {
    const sorted = [...(['LOW', 'CRITICAL', 'MEDIUM', 'HIGH'] as const)].sort(compareSeverity)
    expect(sorted).toEqual(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'])
    expect(SEVERITY_ORDER.CRITICAL).toBeLessThan(SEVERITY_ORDER.LOW)
  })
})
