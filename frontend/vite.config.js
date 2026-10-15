import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  // Las pruebas de pantallas recorren la app completa: en una máquina fría (como la de GitHub
  // Actions) alguna puede pasar de los 5 s por defecto sin estar mal.
  test: { testTimeout: 15000 },
})
