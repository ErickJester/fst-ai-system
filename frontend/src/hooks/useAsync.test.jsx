// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act, cleanup } from '@testing-library/react'
import { useAsyncVivo, CADA_ACTIVO_MS, CADA_INACTIVO_MS } from './useAsync'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

// Deja correr las promesas pendientes con los temporizadores simulados.
const vaciar = () => act(async () => { await Promise.resolve() })

describe('useAsyncVivo', () => {
  it('vuelve a consultar en silencio: más seguido con activos, más espaciado sin ellos', async () => {
    vi.useFakeTimers()
    let activos = true
    const consultar = vi.fn(async () => ({ activos }))
    const { result } = renderHook(() => useAsyncVivo(consultar, [], (d) => (d.activos ? CADA_ACTIVO_MS : CADA_INACTIVO_MS)))
    await vaciar()
    expect(consultar).toHaveBeenCalledTimes(1)
    expect(result.current.loading).toBe(false)

    // A los 5 s consulta otra vez sin pasar por «cargando».
    await act(async () => { vi.advanceTimersByTime(CADA_ACTIVO_MS) })
    expect(result.current.loading).toBe(false)
    await vaciar()
    expect(consultar).toHaveBeenCalledTimes(2)

    // Ya sin análisis activos, la siguiente es a los 30 s.
    activos = false
    await act(async () => { vi.advanceTimersByTime(CADA_ACTIVO_MS) })
    await vaciar()
    expect(consultar).toHaveBeenCalledTimes(3)
    await act(async () => { vi.advanceTimersByTime(CADA_ACTIVO_MS * 2) })
    expect(consultar).toHaveBeenCalledTimes(3)
    await act(async () => { vi.advanceTimersByTime(CADA_INACTIVO_MS) })
    await vaciar()
    expect(consultar).toHaveBeenCalledTimes(4)
  })

  it('si falla una consulta silenciosa conserva los datos que ya mostraba', async () => {
    vi.useFakeTimers()
    let falla = false
    const consultar = vi.fn(async () => {
      if (falla) throw new Error('sin red')
      return { activos: true }
    })
    const { result } = renderHook(() => useAsyncVivo(consultar, [], () => CADA_ACTIVO_MS))
    await vaciar()
    falla = true
    await act(async () => { vi.advanceTimersByTime(CADA_ACTIVO_MS) })
    await vaciar()
    expect(result.current.data).toEqual({ activos: true })
    expect(result.current.error).toBeNull()
  })
})
