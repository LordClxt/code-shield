import { describe, expect, it } from 'vitest'
import { PLATFORMS, detectOs, downloadHref } from '@/components/landing/download'

describe('detectOs', () => {
  it('reconnaît les trois systèmes de bureau', () => {
    expect(detectOs('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15', 'MacIntel')).toBe('macos')
    expect(detectOs('Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Win32')).toBe('windows')
    expect(detectOs('Mozilla/5.0 (X11; Linux x86_64)', 'Linux x86_64')).toBe('linux')
  })

  it('ne propose rien sur mobile', () => {
    expect(detectOs('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'iPhone')).toBeNull()
    expect(detectOs('Mozilla/5.0 (Linux; Android 14; Pixel 8)', 'Linux armv8l')).toBeNull()
  })
})

describe('liens de téléchargement', () => {
  it('suivent les noms servis par serve.ts', () => {
    expect(PLATFORMS.map((p) => downloadHref(p.file))).toEqual([
      '/downloads/CodeShield-macos.dmg',
      '/downloads/CodeShield-windows.msi',
      '/downloads/CodeShield-linux.deb',
    ])
  })
})
