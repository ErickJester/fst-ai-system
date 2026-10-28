// @vitest-environment jsdom
// Modo desarrollador: el banner con la versión y los tiempos del análisis se ven solo con el modo prendido.
import React from 'react'
import { describe, it, expect, afterEach, vi } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { version as versionDelPaquete } from '../package.json'
import { ModoContext } from './contexts/ModoContext'
import BannerVersion from './components/BannerVersion'
import BotonSalidaModelo from './components/BotonSalidaModelo'
import { TrabajoActual } from './pages/ProgresoPage'

afterEach(cleanup)

const conModo = (valor, hijo) => <ModoContext.Provider value={valor}>{hijo}</ModoContext.Provider>
const PRENDIDO = { modoDesarrollador: true, version: '0.22.0' }
const APAGADO = { modoDesarrollador: false, version: '0.22.0' }

describe('banner con la versión', () => {
  it('con modo desarrollador: solo «v0.22.0», sin BETA', () => {
    render(conModo(PRENDIDO, <BannerVersion />))
    const banner = screen.getByText('v0.22.0')
    expect(banner.className).toBe('version-banner')
    expect(banner.getAttribute('role')).toBe('status')
    expect(banner.textContent).not.toMatch(/beta/i)
  })

  it('sin modo desarrollador no hay banner', () => {
    const { container } = render(conModo(APAGADO, <BannerVersion />))
    expect(container.querySelector('.version-banner')).toBeNull()
    expect(screen.queryByText(/^v\d/)).toBeNull()
  })

  it('sin proveedor (por omisión) tampoco, y sin versión del backend usa la del paquete', () => {
    const { container } = render(<BannerVersion />)
    expect(container.querySelector('.version-banner')).toBeNull()
    cleanup()
    render(conModo({ modoDesarrollador: true, version: null }, <BannerVersion />))
    expect(screen.getByText('v' + versionDelPaquete)).toBeTruthy()
  })
})

describe('botón «Salida del modelo» de Resultados', () => {
  it('con modo desarrollador: una tanda descarga un CSV y el grupo completo, un ZIP', () => {
    const { rerender } = render(conModo(PRENDIDO, <BotonSalidaModelo grupoCompleto={false} onClick={() => {}} />))
    expect(screen.getByRole('button', { name: 'Salida del modelo (CSV)' })).toBeTruthy()
    rerender(conModo(PRENDIDO, <BotonSalidaModelo grupoCompleto onClick={() => {}} />))
    expect(screen.getByRole('button', { name: 'Salida del modelo (ZIP)' })).toBeTruthy()
  })

  it('sin modo desarrollador no hay botón, ni siquiera sin proveedor', () => {
    const { container } = render(conModo(APAGADO, <BotonSalidaModelo grupoCompleto={false} onClick={() => {}} />))
    expect(container.querySelector('button')).toBeNull()
    cleanup()
    const solo = render(<BotonSalidaModelo grupoCompleto={false} onClick={() => {}} />)
    expect(solo.container.querySelector('button')).toBeNull()
  })

  it('el clic avisa, y ocupado lo deshabilita', () => {
    const onClick = vi.fn()
    const { rerender } = render(conModo(PRENDIDO, <BotonSalidaModelo grupoCompleto={false} onClick={onClick} />))
    screen.getByRole('button').click()
    expect(onClick).toHaveBeenCalledTimes(1)
    rerender(conModo(PRENDIDO, <BotonSalidaModelo grupoCompleto={false} ocupado onClick={onClick} />))
    expect(screen.getByRole('button').disabled).toBe(true)
  })
})

describe('tiempos en «Análisis en curso»', () => {
  const j = {
    job_id: 1, experimento: 'Exp', grupo: 'G', tanda: 'A', dia: 'DAY2', n_especimenes: 3,
    status: 'RUNNING', stage: 'ROI_DETECTION', progress_pct: 20, stage_pct: 40,
    tiempos: {
      inicio: '2026-10-19T12:00:00-06:00', fin: null, total_s: null, equipo: 'Laptop Erick',
      etapas: {
        PREPROCESSING: { inicio: '2026-10-19T12:00:00-06:00', fin: '2026-10-19T12:00:01-06:00', segundos: 1 },
        ROI_DETECTION: { inicio: '2026-10-19T12:00:01-06:00', fin: null, segundos: null },
      },
    },
  }

  it('con modo desarrollador: cronómetro y tiempo de cada etapa', () => {
    render(conModo(PRENDIDO, <TrabajoActual j={j} />))
    expect(screen.getByRole('timer')).toBeTruthy()
    expect(screen.getByText('Tiempo transcurrido')).toBeTruthy()
    expect(screen.getByText('· Laptop Erick')).toBeTruthy()
    expect(screen.getAllByLabelText('Tiempo de la etapa')).toHaveLength(3)
  })

  it('sin modo desarrollador: ni cronómetro ni tiempos, pero sí la barra de avance', () => {
    render(conModo(APAGADO, <TrabajoActual j={j} />))
    expect(screen.queryByRole('timer')).toBeNull()
    expect(screen.queryByText('Tiempo transcurrido')).toBeNull()
    expect(screen.queryByLabelText('Tiempo de la etapa')).toBeNull()
    expect(screen.queryByText('· Laptop Erick')).toBeNull()
    expect(screen.getByRole('progressbar')).toBeTruthy()
  })
})

describe('getModo: GET /health una sola vez', () => {
  async function cargar(get) {
    vi.resetModules()
    vi.stubEnv('VITE_USE_MOCKS', 'false')
    vi.doMock('./services/api', () => ({ default: { get } }))
    return (await import('./services/modo')).getModo
  }
  afterEach(() => { vi.unstubAllEnvs(); vi.doUnmock('./services/api') })

  it('lee modoDesarrollador y version, y no repite la petición', async () => {
    const get = vi.fn().mockResolvedValue({ data: { estado: 'ok', modoDesarrollador: true, version: '0.22.0' } })
    const getModo = await cargar(get)
    expect(await getModo()).toEqual({ modoDesarrollador: true, version: '0.22.0' })
    await getModo()
    expect(get).toHaveBeenCalledTimes(1)
    expect(get).toHaveBeenCalledWith('/health')
  })

  it('si el backend no contesta, el modo queda apagado', async () => {
    const getModo = await cargar(vi.fn().mockRejectedValue(new Error('sin red')))
    expect((await getModo()).modoDesarrollador).toBe(false)
  })

  it('una respuesta sin el campo (backend viejo) es modo apagado', async () => {
    const getModo = await cargar(vi.fn().mockResolvedValue({ data: { estado: 'ok' } }))
    expect(await getModo()).toEqual({ modoDesarrollador: false, version: versionDelPaquete })
  })
})
