// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act, cleanup } from '@testing-library/react'
import { usePolling } from './usePolling'

let visibilidad = 'visible'
Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibilidad })

function cambiarVisibilidad(estado) {
  visibilidad = estado
  act(() => { document.dispatchEvent(new Event('visibilitychange')) })
}

afterEach(() => {
  cleanup()
  visibilidad = 'visible'
  vi.useRealTimers()
})

describe('usePolling con la pestaña oculta', () => {
  it('no consulta con la pestaña oculta y, al volver, consulta en seguida', async () => {
    vi.useFakeTimers()
    visibilidad = 'hidden'
    const consultar = vi.fn(async () => ({ n: 1 }))
    renderHook(() => usePolling(consultar, 5000))
    await act(async () => { vi.advanceTimersByTime(20000) })
    expect(consultar).not.toHaveBeenCalled()

    await act(async () => { cambiarVisibilidad('visible') })
    expect(consultar).toHaveBeenCalledTimes(1)
  })

  it('se detiene al ocultarse y retoma el intervalo al volver', async () => {
    vi.useFakeTimers()
    const consultar = vi.fn(async () => ({ n: 1 }))
    renderHook(() => usePolling(consultar, 5000))
    await act(async () => { await Promise.resolve() })
    expect(consultar).toHaveBeenCalledTimes(1)

    await act(async () => { vi.advanceTimersByTime(5000) })
    expect(consultar).toHaveBeenCalledTimes(2)

    await act(async () => { cambiarVisibilidad('hidden') })
    await act(async () => { vi.advanceTimersByTime(20000) })
    expect(consultar).toHaveBeenCalledTimes(2)

    await act(async () => { cambiarVisibilidad('visible') })
    expect(consultar).toHaveBeenCalledTimes(3)
    await act(async () => { vi.advanceTimersByTime(5000) })
    expect(consultar).toHaveBeenCalledTimes(4)
  })
})
