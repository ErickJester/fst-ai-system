import { describe, it, expect } from 'vitest'
import { tiemposFront } from './progreso'
import { tiempoAnalisisFront } from './results'

describe('tiempos de /status y /results → lo que usan las pantallas', () => {
  const status = {
    inicio: '2026-10-19T12:00:00-06:00',
    fin: null,
    totalSegundos: null,
    etapas: [
      { etapa: 'preprocesamiento', inicio: '2026-10-19T12:00:00-06:00', fin: '2026-10-19T12:00:01-06:00', segundos: 1 },
      { etapa: 'cilindros', inicio: '2026-10-19T12:00:01-06:00', fin: null, segundos: null },
    ],
    equipo: { nombre: null, cpu: 'Intel i7', nucleos: 8, memoriaGB: 7.6, dockerCPU: 8 },
  }

  it('las etapas con las claves del frontend, y la que corre sin fin ni segundos', () => {
    const t = tiemposFront(status)
    expect(Object.keys(t.etapas)).toEqual(['PREPROCESSING', 'ROI_DETECTION'])
    expect(t.etapas.PREPROCESSING).toEqual({ inicio: '2026-10-19T12:00:00-06:00', fin: '2026-10-19T12:00:01-06:00', segundos: 1 })
    expect(t.etapas.ROI_DETECTION.fin).toBeNull()
    expect(t.etapas.ROI_DETECTION.segundos).toBeNull()
    expect(t.total_s).toBeNull()
    expect(t.equipo).toBe('Intel i7')
  })

  it('terminado: total y el nombre del equipo', () => {
    const t = tiemposFront({ ...status, fin: '2026-10-19T12:20:05-06:00', totalSegundos: 1205, equipo: { nombre: 'Laptop Erick', cpu: 'Intel i7' } })
    expect(t.total_s).toBe(1205)
    expect(t.equipo).toBe('Laptop Erick')
  })

  it('sin registro (análisis viejo) es null; una etapa que no se conoce se ignora', () => {
    expect(tiemposFront(null)).toBeNull()
    expect(Object.keys(tiemposFront({ ...status, etapas: [{ etapa: 'otra', inicio: 'x', fin: null, segundos: null }] }).etapas)).toEqual([])
  })

  it('/results: total y equipo de la tanda, o null si no hay registro', () => {
    expect(tiempoAnalisisFront({ tiempoAnalisis: { totalSegundos: 1205, equipo: 'Laptop Erick' } })).toEqual({ total_s: 1205, equipo: 'Laptop Erick' })
    expect(tiempoAnalisisFront({ tiempoAnalisis: null })).toBeNull()
    expect(tiempoAnalisisFront({ tiempoAnalisis: { totalSegundos: null, equipo: 'x' } })).toBeNull()
    expect(tiempoAnalisisFront(undefined)).toBeNull()
  })
})
