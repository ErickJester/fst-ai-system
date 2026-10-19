import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  // Las pruebas de pantallas recorren la app completa: en una máquina fría (como la de GitHub
  // Actions) alguna puede pasar de los 5 s por defecto sin estar mal.
  // Las pruebas usan siempre los datos de ejemplo, aunque el .env local apunte al backend real.
  test: { testTimeout: 15000, env: { VITE_USE_MOCKS: 'true', VITE_API_REAL: '' } },
})
