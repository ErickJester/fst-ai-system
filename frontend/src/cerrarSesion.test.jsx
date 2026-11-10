// @vitest-environment jsdom
// Cerrar sesión descarta el token: en la app completa, con la cuenta de ejemplo y con la cuenta de primer acceso.
// La cabecera de autorización se comprueba llamando al interceptor de api.js: en el modo de ejemplo no hay
// peticiones HTTP, así que no se puede ver la cabecera en una petición real.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import App from './App'
import api from './services/api'
import { reiniciarDatos } from './services/mocks/data'
import { TOKEN_KEY } from './services/config'

window.scrollTo = vi.fn()

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

// Cabecera Authorization que pondría el interceptor de api.js a una petición nueva.
function cabeceraDeUnaPeticion() {
  const interceptor = api.interceptors.request.handlers[0].fulfilled
  return interceptor({ headers: {} }).headers.Authorization
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  reiniciarDatos()
})
afterEach(() => cleanup())

describe('cerrar sesión descarta el token', () => {
  it('con sesión iniciada, el token está en localStorage y las peticiones lo mandan', async () => {
    const user = abrir('/login')
    await entrar(user, 'mrivera@ipn.mx')
    expect(await screen.findByRole('heading', { name: 'Experimentos' })).toBeTruthy()
    expect(localStorage.getItem(TOKEN_KEY)).toMatch(/^mock-token-/)
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(cabeceraDeUnaPeticion()).toBe(`Bearer ${localStorage.getItem(TOKEN_KEY)}`)
  })

  it('«Cerrar sesión» del menú de usuario borra el token y lleva al login', async () => {
    const user = abrir('/login')
    await entrar(user, 'mrivera@ipn.mx')
    await screen.findByRole('heading', { name: 'Experimentos' })
    await user.click(screen.getByRole('button', { name: 'Menú de usuario' }))
    await user.click(screen.getByRole('link', { name: 'Cerrar sesión' }))
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem('fst.user')).toBeNull()
  })

  it('después de cerrar sesión, una ruta protegida redirige al login y las peticiones no llevan la cabecera', async () => {
    const user = abrir('/login')
    await entrar(user, 'mrivera@ipn.mx')
    await screen.findByRole('heading', { name: 'Experimentos' })
    await user.click(screen.getByRole('button', { name: 'Menú de usuario' }))
    await user.click(screen.getByRole('link', { name: 'Cerrar sesión' }))
    await screen.findByRole('heading', { name: 'Iniciar sesión' })
    expect(cabeceraDeUnaPeticion()).toBeUndefined()

    // Otra apertura de la app, en /experimentos: sin token no hay sesión y vuelve al login.
    cleanup()
    abrir('/experimentos')
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Experimentos' })).toBeNull()
  })

  it('con la cuenta de primer acceso, «Cerrar sesión» de esa pantalla también borra el token', async () => {
    const user = abrir('/login')
    await entrar(user, 'nueva.cuenta@ipn.mx', 'temporal-1')
    expect(await screen.findByRole('heading', { name: 'Cambiar contraseña' })).toBeTruthy()
    expect(localStorage.getItem(TOKEN_KEY)).toMatch(/^mock-token-/)
    await user.click(screen.getByRole('link', { name: 'Cerrar sesión' }))
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(cabeceraDeUnaPeticion()).toBeUndefined()
  })
})
