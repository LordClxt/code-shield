import { describe, expect, it } from 'vitest'
import syncRequest from '../../../../contracts/fixtures/sync_request.json'
import syncResponse from '../../../../contracts/fixtures/sync_response.json'
import { assertNoSourceCode, mcpAddCommand } from '../api'
import { computeScore } from '../score'
import { SEVERITY_META, TOOL_LABEL } from '../severity'
import type { Finding, SyncResult } from '../types'
import { CATEGORIES, SEVERITIES, TOOLS } from '../types'
import { location } from '../utils'

/**
 * Alignement des types (AGENTS.md) : la même fixture est testée côté Go
 * (backend-go/test) et Kotlin (desktop ContractFixturesTest).
 */
describe('contrat partagé contracts/fixtures', () => {
  it('la requête de synchro est un payload valide et sans code source', () => {
    const findings: Finding[] = syncRequest.findings as Finding[]
    expect(findings).toHaveLength(3)
    expect(() => assertNoSourceCode(syncRequest)).not.toThrow()
    for (const f of findings) {
      expect(TOOLS).toContain(f.tool)
      expect(SEVERITIES).toContain(f.severity)
      if (f.category) expect(CATEGORIES).toContain(f.category)
    }
  })

  it('la réponse de synchro a un score cohérent avec computeScore', () => {
    const r = syncResponse as SyncResult
    expect(computeScore(r.open_counts)).toEqual({ score: r.score, grade: r.grade })
  })

  it('chaque outil et chaque sévérité a un libellé et une couleur', () => {
    for (const t of TOOLS) expect(TOOL_LABEL[t]).toBeTruthy()
    for (const s of SEVERITIES) expect(SEVERITY_META[s].color).toMatch(/^#[0-9A-F]{6}$/i)
  })
})

describe('parité du score avec le backend Go (arrondi bancaire)', () => {
  it('2.5 -> 2 et 3.5 -> 4', () => {
    expect(computeScore({ LOW: 5 }).score).toBe(98)
    expect(computeScore({ LOW: 7 }).score).toBe(96)
  })
})

describe('utilitaires', () => {
  it('commande MCP pré-remplie', () => {
    expect(mcpAddCommand('csk_abc', 'https://api.x.dev/')).toBe(
      'claude mcp add --transport http codeshield https://api.x.dev/mcp --header "Authorization: Bearer csk_abc"',
    )
  })

  it('emplacement avec plage de lignes', () => {
    expect(location({ file_path: 'a.py', line_number: 3 })).toBe('a.py:3')
    expect(location({ file_path: 'a.py', line_number: 3, end_line: 5 })).toBe('a.py:3-5')
    expect(location({ file_path: 'a.py', line_number: 3, end_line: 3 })).toBe('a.py:3')
  })
})
