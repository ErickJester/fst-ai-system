// @vitest-environment jsdom
// Revisión automática de accesibilidad (axe-core) de cada pantalla: etiquetas de campos,
// nombres de botones y enlaces, roles ARIA, encabezados, ids repetidos… El contraste de color
// no se revisa aquí: jsdom no calcula estilos.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import axe from 'axe-core'
import { AuthProvider } from './contexts/AuthContext'
import App from './App'
import { reiniciarDatos } from './services/mocks/data'

window.scrollTo = vi.fn()
URL.createObjectURL = vi.fn(() => 'blob:prueba')
URL.revokeObjectURL = vi.fn()
HTMLCanvasElement.prototype.getContext = () => null

function abrir(ruta) {
  render(
    <MemoryRouter initialEntries={[ruta]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>
  )
}

// Inicia sesión con la cuenta dada y abre la ruta.
async function conSesion(ruta, correo = 'mrivera@ipn.mx') {
  abrir('/login')
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Correo electrónico'), correo)
  await user.type(screen.getByLabelText('Contraseña'), 'x')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  await screen.findByRole('heading', { name: 'Experimentos' })
  cleanup()
  abrir(ruta)
}

// Problemas que encuentra axe, como texto legible: regla, cuántos y dónde.
async function problemas() {
  const r = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })
  return r.violations.map((v) => v.id + ' (' + v.nodes.length + '): ' + v.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' | '))
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  reiniciarDatos()
})
afterEach(() => cleanup())

const PANTALLAS = [
  ['Experimentos', '/experimentos', 'Experimentos'],
  ['Nuevo experimento', '/experimentos/nuevo', 'Datos generales'],
  ['Experimento', '/experimentos/7', 'Compuesto CSR-14 · curva de dosis'],
  ['Grupo', '/experimentos/7/grupos/2', /^Referencia/],
  ['Cargar video', '/experimentos/7/cargar', 'Cargar video por tanda'],
  ['Análisis en curso', '/analisis', 'Análisis en curso'],
  ['Pausa', '/experimentos/7/analisis/502/pausa', /No se ve al espécimen/],
  ['Resultados', '/experimentos/7/resultados?grupo=2&tanda=A', /^Resultados/],
  ['Revisión', '/experimentos/7/analisis/402/revision', /^Revisión segundo a segundo/],
  ['Mi perfil', '/perfil', 'Mi perfil'],
]

describe('accesibilidad (axe-core)', () => {
  it('Inicio de sesión', async () => {
    abrir('/login')
    await screen.findByRole('heading', { name: 'Iniciar sesión' })
    expect(await problemas()).toEqual([])
  })

  for (const [nombre, ruta, titulo] of PANTALLAS) {
    it(nombre, async () => {
      await conSesion(ruta)
      await screen.findByRole('heading', { name: titulo })
      expect(await problemas()).toEqual([])
    })
  }

  it('Administración', async () => {
    await conSesion('/admin', 'creyesl@ipn.mx')
    await screen.findByRole('heading', { name: 'Administración' })
    await screen.findByRole('columnheader', { name: 'Experimentos' })
    expect(await problemas()).toEqual([])
  })
})
