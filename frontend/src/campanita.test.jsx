// @vitest-environment jsdom
// La campanita: el número es el total que da el servidor (no las no leídas de la lista) y no consulta con la pestaña oculta.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

vi.mock('./contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: '2019630177', ini: 'AB', admin: false }, logout: vi.fn() }),
}))
vi.mock('./services/notifications', () => ({
  listNotifications: vi.fn(),
  unreadCount: vi.fn(),
  setRead: vi.fn(),
  markAllRead: vi.fn(),
}))

import { listNotifications, unreadCount, setRead, markAllRead } from './services/notifications'
import Topbar from './components/Topbar'

// Dos notificaciones, las dos leídas: si la campanita contara la lista, no mostraría nada.
const leidas = [
  { id: 1, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', texto: 'ok', enlace: '/x', creada: '2026-10-28T10:00:00', is_read: true },
  { id: 2, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', texto: 'ok', enlace: '/x', creada: '2026-10-28T09:00:00', is_read: true },
]

const cambiarVisibilidad = (valor) => {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => valor })
}

beforeEach(() => {
  listNotifications.mockReset().mockResolvedValue(leidas)
  unreadCount.mockReset().mockResolvedValue(7)
  setRead.mockReset().mockResolvedValue({})
  markAllRead.mockReset().mockResolvedValue({})
  cambiarVisibilidad('visible')
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

const abrir = () => render(<MemoryRouter><Topbar /></MemoryRouter>)

describe('campanita: el total lo da el servidor', () => {
  it('el punto y el número son los de unreadCount, aunque la lista traiga solo leídas', async () => {
    const { container } = abrir()
    await act(async () => { await Promise.resolve() })
    expect(container.querySelector('.tb-dot')).not.toBeNull()
    await userEvent.click(screen.getByRole('button', { name: 'Notificaciones' }))
    expect(screen.getByText('7 sin leer')).toBeTruthy()
  })

  it('sin no leídas en el servidor no hay punto ni número', async () => {
    unreadCount.mockResolvedValue(0)
    const { container } = abrir()
    await act(async () => { await Promise.resolve() })
    expect(container.querySelector('.tb-dot')).toBeNull()
  })

  it('al marcar una como leída, el número baja en seguida y se vuelve a pedir al servidor', async () => {
    const lista = [{ ...leidas[0], is_read: false }]
    listNotifications.mockResolvedValue(lista)
    unreadCount.mockResolvedValueOnce(1).mockResolvedValueOnce(0)
    abrir()
    await act(async () => { await Promise.resolve() })
    await userEvent.click(screen.getByRole('button', { name: 'Notificaciones' }))
    await userEvent.click(screen.getByRole('button', { name: 'Marcar como leída' }))
    await act(async () => { await Promise.resolve() })
    expect(setRead).toHaveBeenCalledWith(1, true)
    expect(unreadCount.mock.calls.length).toBeGreaterThanOrEqual(2)
  })
})

describe('campanita: no consulta con la pestaña oculta', () => {
  it('con la pestaña oculta no consulta; al volver a verla, consulta en seguida', async () => {
    vi.useFakeTimers()
    abrir()
    await act(async () => { await Promise.resolve() })
    const antes = listNotifications.mock.calls.length
    expect(antes).toBeGreaterThanOrEqual(1)

    cambiarVisibilidad('hidden')
    await act(async () => { vi.advanceTimersByTime(90000) })
    expect(listNotifications.mock.calls.length).toBe(antes)
    expect(unreadCount.mock.calls.length).toBe(antes)

    cambiarVisibilidad('visible')
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')) })
    expect(listNotifications.mock.calls.length).toBe(antes + 1)
    expect(unreadCount.mock.calls.length).toBe(antes + 1)
  })

  it('con la pestaña visible, sigue consultando cada 30 s', async () => {
    vi.useFakeTimers()
    abrir()
    await act(async () => { await Promise.resolve() })
    const antes = listNotifications.mock.calls.length
    await act(async () => { vi.advanceTimersByTime(30000) })
    expect(listNotifications.mock.calls.length).toBe(antes + 1)
  })
})
