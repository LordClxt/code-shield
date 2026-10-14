import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

// Config de test séparée : le plugin Start n'est pas nécessaire pour tester `lib/`.
export default defineConfig({
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
  },
})
