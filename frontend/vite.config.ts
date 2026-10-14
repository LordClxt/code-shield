import tailwindcss from '@tailwindcss/vite'
import { tanstackStart } from '@tanstack/react-start/plugin/vite'
import viteReact from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import tsConfigPaths from 'vite-tsconfig-paths'

// TanStack Start : routage par fichiers (src/routes -> routeTree.gen.ts), SSR
// pour la landing, rendu client pour l'espace connecté (`ssr: false`).
export default defineConfig({
  server: { port: 3000 },
  plugins: [tsConfigPaths(), tailwindcss(), tanstackStart(), viteReact()],
})
