// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import axe from 'axe-core'
import { AuthProvider } from '../contexts/AuthContext'
import AvisoPrivacidadPage from './AvisoPrivacidadPage'
import TerminosPage from './TerminosPage'

// Sin sesión: no hay token ni usuario guardados; las páginas deben verse igual.
function renderSinSesion(Pagina) {
  return render(
    <MemoryRouter initialEntries={['/aviso']}>
      <AuthProvider>
        <Pagina />
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  cleanup()
})

describe('AvisoPrivacidadPage', () => {
  it('muestra su título y sus tres secciones, sin pedir sesión', () => {
    renderSinSesion(AvisoPrivacidadPage)
    expect(screen.getByRole('heading', { level: 2, name: 'Aviso de privacidad' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 3, name: 'Qué datos personales se tratan' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 3, name: 'Para qué se usan' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 3, name: 'Cómo se protegen' })).toBeTruthy()
  })

  it('lista los datos, los usos y la protección de la contraseña, y tiene la nota de redacción', () => {
    renderSinSesion(AvisoPrivacidadPage)
    expect(screen.getByText('Correo electrónico.')).toBeTruthy()
    expect(screen.getByText(/hash y sal/)).toBeTruthy()
    expect(screen.getByText(/Este texto se ajustará a la redacción oficial/)).toBeTruthy()
  })

  it('tiene un enlace «Volver»', () => {
    renderSinSesion(AvisoPrivacidadPage)
    expect(screen.getByRole('link', { name: 'Volver' })).toBeTruthy()
  })

  it('sin fallos de axe-core', async () => {
    const { container } = renderSinSesion(AvisoPrivacidadPage)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})

describe('TerminosPage', () => {
  it('muestra su título y sus secciones, sin pedir sesión', () => {
    renderSinSesion(TerminosPage)
    expect(screen.getByRole('heading', { level: 2, name: 'Términos y condiciones de uso' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 3, name: 'Uso del sistema' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 3, name: 'Resultados' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 3, name: 'Videos y conservación' })).toBeTruthy()
  })

  it('dice que no emite diagnóstico y que los videos se borran a los 30 días', () => {
    renderSinSesion(TerminosPage)
    expect(screen.getByText('No emite diagnóstico clínico.')).toBeTruthy()
    expect(screen.getByText(/Los videos se borran 30 días después de un análisis exitoso/)).toBeTruthy()
    expect(screen.getByText(/Los resultados y los reportes se conservan/)).toBeTruthy()
  })

  it('tiene un enlace «Volver» y la nota de redacción', () => {
    renderSinSesion(TerminosPage)
    expect(screen.getByRole('link', { name: 'Volver' })).toBeTruthy()
    expect(screen.getByText(/Este texto se ajustará a la redacción oficial/)).toBeTruthy()
  })

  it('sin fallos de axe-core', async () => {
    const { container } = renderSinSesion(TerminosPage)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})
