// @vitest-environment jsdom
// El PDF de diagnóstico de un análisis con error (M12, punto 28): reintentos con tope y sin reintentar si el archivo ya no existe.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, fireEvent, act } from '@testing-library/react'

vi.mock('./services/queue', () => ({ getControl: vi.fn(async () => null), getDiagnostico: vi.fn() }))

import { getDiagnostico } from './services/queue'
import { TrabajoFallido } from './pages/ProgresoPage'

const trabajo = {
  clave: '7', job_id: 402, experimento: 'Compuesto CSR-14', grupo: 'Control', tanda: 'A', dia: 'DAY2',
  n_especimenes: 4, stage: 'PREPROCESSING', progress_pct: 0, control_disponible: false, tiempos: null,
}

const error404 = (texto) => Object.assign(new Error('404'), { response: { status: 404, data: { error: texto } } })
const SIN_REPORTE = 'El análisis 402 no tiene reporte de diagnóstico'
const BORRADO = 'El archivo de diagnóstico del análisis 402 ya no existe'

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:diagnostico')
  URL.revokeObjectURL = vi.fn()
  getDiagnostico.mockReset()
  vi.useFakeTimers()
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

const boton = (nombre) => screen.getByRole('button', { name: nombre })

describe('descarga del diagnóstico con reintentos', () => {
  it('reintenta como mucho 15 veces y después muestra un texto claro y el botón para volver a intentar', async () => {
    getDiagnostico.mockRejectedValue(error404(SIN_REPORTE))
    render(<TrabajoFallido j={trabajo} />)
    await act(async () => { fireEvent.click(boton('Descargar reporte de diagnóstico (PDF)')) })
    expect(getDiagnostico).toHaveBeenCalledTimes(1)
    expect(boton('Preparando…')).toBeTruthy()

    await act(async () => { await vi.advanceTimersByTimeAsync(4000 * 30) })
    expect(getDiagnostico).toHaveBeenCalledTimes(16) // el primero más 15 reintentos
    expect(screen.getByText(/sigue sin estar listo/)).toBeTruthy()
    expect(boton('Volver a intentar')).toBeTruthy()
  })

  it('si el PDF ya no existe, no reintenta y lo dice', async () => {
    getDiagnostico.mockRejectedValue(error404(BORRADO))
    render(<TrabajoFallido j={trabajo} />)
    await act(async () => { fireEvent.click(boton('Descargar reporte de diagnóstico (PDF)')) })
    await act(async () => { await vi.advanceTimersByTimeAsync(4000 * 5) })
    expect(getDiagnostico).toHaveBeenCalledTimes(1)
    expect(screen.getByText(/ya no está en el servidor/)).toBeTruthy()
  })

  it('«Volver a intentar» pide el PDF a mano y, si ya está, lo descarga', async () => {
    getDiagnostico.mockRejectedValueOnce(error404(BORRADO))
    getDiagnostico.mockResolvedValueOnce(new Blob(['%PDF'], { type: 'application/pdf' }))
    render(<TrabajoFallido j={trabajo} />)
    await act(async () => { fireEvent.click(boton('Descargar reporte de diagnóstico (PDF)')) })
    expect(boton('Volver a intentar')).toBeTruthy()
    await act(async () => { fireEvent.click(boton('Volver a intentar')) })
    expect(getDiagnostico).toHaveBeenCalledTimes(2)
    expect(URL.createObjectURL).toHaveBeenCalled()
  })
})
