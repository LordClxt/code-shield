import { describe, expect, it } from 'vitest'
import { assertNoSourceCode } from '../api'

describe('assertNoSourceCode (Sécurité Intellectuelle)', () => {
  it('accepte un payload de métadonnées valide', () => {
    expect(() =>
      assertNoSourceCode({
        project_id: 1,
        findings: [
          {
            tool: 'SEMGREP',
            rule_id: 'R',
            file_path: 'src/a.py',
            line_number: 10,
            severity: 'HIGH',
            title: 't',
          },
        ],
      }),
    ).not.toThrow()
  })

  it.each(['source_code', 'snippet', 'code', 'source', 'raw_code'])(
    'rejette la clé interdite "%s" même imbriquée',
    (key) => {
      expect(() =>
        assertNoSourceCode({ findings: [{ file_path: 'a.py', [key]: 'print(1)' }] }),
      ).toThrow(/code source brut est interdit/)
    },
  )

  it('inspecte les tableaux récursivement', () => {
    expect(() => assertNoSourceCode([{ a: [{ source_code: 'x' }] }])).toThrow()
  })
})
