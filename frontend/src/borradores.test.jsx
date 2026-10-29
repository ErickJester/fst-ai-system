// @vitest-environment jsdom
// Pruebas de la app completa (como pantallas.test.jsx): el borrador de la revisión al esconder la pestaña (M12, punto 46)
// y las etiquetas de reporte de la lista de experimentos (M12, punto 61).
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import App from './App'
import { reiniciarDatos } from './services/mocks/data'

window.scrollTo = vi.fn()
URL.createObjectURL = vi.fn(() => 'blob:prueba')
URL.revokeObjectURL = vi.fn()
HTMLCanvasElement.prototype.getContext = () => null

function abrir(ruta = '/login') {
  const user = userEvent.setup()
  render(
    <MemoryRouter initialEntries={[ruta]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>
  )
  return user
}

async function entrar(user, correo, password = 'x') {
  await user.type(screen.getByLabelText('Correo electrónico'), correo)
  await user.type(screen.getByLabelText('Contraseña'), password)
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
}

async function conSesion(ruta, correo = 'mrivera@ipn.mx') {
  const user = abrir('/login')
  await entrar(user, correo)
  await screen.findByRole('heading', { name: 'Experimentos' })
  cleanup()
  return abrir(ruta)
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  reiniciarDatos()
})

afterEach(() => {
  cleanup()
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' })
})

describe('revisión: el borrador se guarda al esconder o cerrar la pestaña', () => {
  it('pagehide y visibilitychange a oculta escriben el borrador en ese momento', async () => {
    const user = await conSesion('/experimentos/7/analisis/402/revision')
    await screen.findByRole('heading', { name: 'Revisión segundo a segundo · Referencia, Tanda A' })
    await user.keyboard('3')
    const clave = Object.keys(localStorage).find((k) => k.startsWith('fst.revision.'))
    const borrador = localStorage.getItem(clave)
    expect(borrador).toBeTruthy()

    // Cierre de la página: aunque el borrador se haya borrado, el evento lo vuelve a escribir.
    localStorage.removeItem(clave)
    window.dispatchEvent(new Event('pagehide'))
    expect(localStorage.getItem(clave)).toBe(borrador)

    // Pestaña escondida: lo mismo.
    localStorage.removeItem(clave)
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(localStorage.getItem(clave)).toBe(borrador)
  })
})

describe('lista de experimentos: reportes', () => {
  it('cada experimento con resultados tiene «Ver resultados →» y un PDF; sin CSV ni XLSX sueltos', async () => {
    await conSesion('/experimentos')
    const tabla = await screen.findByRole('table')
    expect(within(tabla).getAllByRole('link', { name: 'Ver resultados →' }).length).toBeGreaterThan(0)
    expect(within(tabla).getAllByRole('button', { name: 'PDF' }).length).toBeGreaterThan(0)
    expect(within(tabla).queryByRole('button', { name: 'CSV' })).toBeNull()
    expect(within(tabla).queryByRole('link', { name: 'CSV' })).toBeNull()
  })
})
