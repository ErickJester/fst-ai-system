// @vitest-environment jsdom
// Pruebas de las pantallas: la app completa, usada como lo haría una persona
// (escribir, hacer clic, leer lo que aparece). Usan los datos de ejemplo.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import App from './App'
import { reiniciarDatos } from './services/mocks/data'

// Lo que jsdom no trae: desplazar la ventana, URLs de archivos y leer metadatos de video.
window.scrollTo = vi.fn()
URL.createObjectURL = vi.fn(() => 'blob:prueba')
URL.revokeObjectURL = vi.fn()
let videoAncho = 1280
let videoAlto = 720
Object.defineProperty(HTMLMediaElement.prototype, 'src', {
  configurable: true,
  set() { setTimeout(() => this.onloadedmetadata?.()) },
})
Object.defineProperty(HTMLMediaElement.prototype, 'duration', { configurable: true, get: () => 300 })
Object.defineProperty(HTMLVideoElement.prototype, 'videoWidth', { configurable: true, get: () => videoAncho })
Object.defineProperty(HTMLVideoElement.prototype, 'videoHeight', { configurable: true, get: () => videoAlto })

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
  await user.type(screen.getByLabelText('Correo institucional'), correo)
  await user.type(screen.getByLabelText('Contraseña'), password)
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
}

// Inicia sesión y abre directamente otra ruta (la sesión queda en sessionStorage).
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
  videoAncho = 1280
  videoAlto = 720
})
afterEach(() => cleanup())

describe('acceso', () => {
  it('entra con una cuenta de ejemplo y llega a Experimentos', async () => {
    const user = abrir('/login')
    await entrar(user, 'mrivera@ipn.mx')
    expect(await screen.findByRole('heading', { name: 'Experimentos' })).toBeTruthy()
    expect(screen.getByText('Compuesto CSR-14 · curva de dosis')).toBeTruthy()
  })

  it('marca el correo que no es @ipn.mx', async () => {
    const user = abrir('/login')
    await entrar(user, 'alguien@gmail.com')
    expect(await screen.findByText('Usa tu correo institucional @ipn.mx.')).toBeTruthy()
    expect(screen.getByLabelText('Correo institucional').className).toContain('error')
  })

  it('sin sesión, cualquier ruta lleva al login', async () => {
    abrir('/experimentos/EXP-2026-02')
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy()
  })

  it('una cuenta nueva pasa por el primer acceso antes de entrar', async () => {
    const user = abrir('/login')
    await entrar(user, 'nueva.cuenta@ipn.mx', 'temporal-1')
    expect(await screen.findByRole('heading', { name: 'Cambiar contraseña' })).toBeTruthy()
    await user.type(screen.getByLabelText('Contraseña temporal'), 'temporal-1')
    await user.type(screen.getByLabelText('Nueva contraseña'), 'mi-clave-123')
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'mi-clave-123')
    await user.click(screen.getByRole('button', { name: 'Guardar y entrar' }))
    expect(await screen.findByRole('heading', { name: 'Experimentos' })).toBeTruthy()
  })

  it('recuperar la contraseña con el enlace y entrar con la nueva', async () => {
    const user = abrir('/login')
    await user.click(screen.getByText('Olvidé mi contraseña'))
    await user.type(screen.getByLabelText('Correo institucional'), 'mrivera@ipn.mx')
    await user.click(screen.getByRole('button', { name: 'Enviar enlace' }))
    expect(await screen.findByText('Si el correo está registrado, recibirás un enlace.')).toBeTruthy()

    await user.click(screen.getByText('abrir el enlace que llegaría por correo'))
    await user.type(await screen.findByLabelText('Nueva contraseña'), 'clave-nueva-9')
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'clave-nueva-9')
    await user.click(screen.getByRole('button', { name: 'Guardar contraseña' }))
    expect(await screen.findByRole('heading', { name: 'Contraseña actualizada' })).toBeTruthy()

    await user.click(screen.getByRole('link', { name: 'Iniciar sesión' }))
    await entrar(user, 'mrivera@ipn.mx', 'clave-nueva-9')
    expect(await screen.findByRole('heading', { name: 'Experimentos' })).toBeTruthy()
  })
})

describe('administración', () => {
  it('crear una cuenta muestra la contraseña temporal una sola vez', async () => {
    const user = abrir('/login')
    await entrar(user, 'creyesl@ipn.mx')
    await user.click(await screen.findByRole('link', { name: 'Administración' }))
    await user.click(await screen.findByRole('button', { name: 'Crear cuenta' }))
    await user.type(screen.getByLabelText('Nombre'), 'Andrea')
    await user.type(screen.getByLabelText('Apellidos'), 'Barrera Solís')
    await user.type(screen.getByLabelText('Correo institucional (@ipn.mx)'), 'abarrera@ipn.mx')
    await user.type(screen.getByLabelText(/Identificador institucional/), '2021630154')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Cuenta creada' })
    expect(within(dialogo).getByText(/no se volverá a mostrar/)).toBeTruthy()
    await user.keyboard('{Escape}') // no se cierra con Escape
    expect(screen.getByRole('dialog')).toBeTruthy()
    await user.click(within(dialogo).getByRole('button', { name: 'Listo' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByText('A. Barrera Solís')).toBeTruthy()
  })
})

describe('cargar video', () => {
  it('elegir el grupo con el teclado, confirmar la tanda y encolar el video', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/cargar')
    const grupo = await screen.findByRole('combobox')
    await user.type(grupo, 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    expect(grupo.value).toBe('Referencia · Fluoxetina 10 mg/kg')
    expect(screen.getByLabelText('Tanda').value).toBe('C')

    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    const archivo = new File(['video'], 'tanda_C.mp4', { type: 'video/mp4' })
    await user.upload(document.querySelector('input[type=file]'), archivo)
    await screen.findByText('tanda_C.mp4')
    await user.click(screen.getByRole('button', { name: 'Guardar y encolar análisis' }))
    expect(await screen.findByText('Video almacenado · análisis en cola')).toBeTruthy()
    expect(screen.getByText(/posición 4 en la cola/)).toBeTruthy()
  })

  it('rechaza un video vertical', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/cargar')
    await screen.findByRole('combobox')
    videoAncho = 720
    videoAlto = 1280
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], 'celular.mp4', { type: 'video/mp4' }))
    expect(await screen.findByText('El video es vertical')).toBeTruthy()
  })
})

describe('resultados', () => {
  it('muestra los cilindros de la tanda y la comparación entre grupos', async () => {
    await conSesion('/experimentos/EXP-2026-02/resultados?grupo=G-02&tanda=A')
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo referencia, Tanda A' })).toBeTruthy()
    expect(screen.getAllByText('Cilindro P1').length).toBeGreaterThan(0)
    expect(await screen.findByText('Este grupo mezcla niveles de clasificación; se muestran por separado.')).toBeTruthy()
  })
})
