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
  it('muestra su título y sus siete secciones, sin pedir sesión', () => {
    renderSinSesion(AvisoPrivacidadPage)
    expect(screen.getByRole('heading', { level: 2, name: 'Aviso de privacidad' })).toBeTruthy()
    for (const nombre of ['Responsable', 'Datos que se tratan', 'Para qué se usan', 'Cómo se protegen', 'Transferencias', 'Derechos', 'Cambios']) {
      expect(screen.getByRole('heading', { level: 3, name: nombre })).toBeTruthy()
    }
  })

  it('dice quién es el responsable, qué datos se tratan, cómo se protege la contraseña y a quién se escribe para ejercer los derechos', () => {
    renderSinSesion(AvisoPrivacidadPage)
    expect(screen.getByText(/Laboratorio de Bioquímica Estructural/)).toBeTruthy()
    expect(screen.getByText(/Identificador institucional, nombre, apellidos y correo electrónico/)).toBeTruthy()
    expect(screen.getByText(/no están destinados a contener datos personales/)).toBeTruthy()
    expect(screen.getByText(/hash y sal/)).toBeTruthy()
    expect(screen.getByText(/Unidad de Transparencia del IPN/)).toBeTruthy()
    expect(screen.getByText(/LGPDPPSO/)).toBeTruthy()
    expect(screen.getByText(/cancelar una cuenta significa desactivarla/)).toBeTruthy()
  })

  it('declara que los avisos por correo salen con una cuenta de Gmail y que no se comparten datos con otros terceros', () => {
    renderSinSesion(AvisoPrivacidadPage)
    expect(screen.getByText(/cuenta de Gmail y una contraseña de aplicación/)).toBeTruthy()
    expect(screen.getByText(/Fuera de eso, los datos no se comparten con terceros/)).toBeTruthy()
    expect(screen.queryByText(/redacción oficial/)).toBeNull()
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
    expect(screen.getByRole('heading', { level: 3, name: 'Uso de la cuenta' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 3, name: 'Alcance' })).toBeTruthy()
  })

  it('dice que la cuenta es personal, que todos ven los experimentos y que las cuentas no se borran', () => {
    renderSinSesion(TerminosPage)
    expect(screen.getByText(/La cuenta es personal/)).toBeTruthy()
    expect(screen.getByText(/Toda cuenta activa puede ver los experimentos y resultados de todo el laboratorio/)).toBeTruthy()
    expect(screen.getByText('Las cuentas se desactivan, nunca se borran.')).toBeTruthy()
    expect(screen.getByText(/prototipo desarrollado como trabajo terminal/)).toBeTruthy()
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
    expect(screen.queryByText(/redacción oficial/)).toBeNull()
  })

  it('sin fallos de axe-core', async () => {
    const { container } = renderSinSesion(TerminosPage)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})
