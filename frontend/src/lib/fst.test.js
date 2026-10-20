import { describe, it, expect } from 'vitest'
import { mean, variance, fmt, r1, fechaCorta, fechaRango, norm, esCorreo, primeraTandaLista, enlaceResultados, pctDeEtapa, PESO_ETAPA } from './fst'

describe('porcentaje de cada etapa en Progreso', () => {
  const ETAPAS = ['PREPROCESSING', 'ROI_DETECTION', 'CLASSIFICATION']

  it('hecha 100, en curso el del backend, pendiente 0', () => {
    const j = { stage: 'ROI_DETECTION', stage_pct: 41 }
    expect(ETAPAS.map((e) => pctDeEtapa(j, e, ETAPAS))).toEqual([100, 41, 0])
  })

  it('en curso sin porcentaje de etapa (backend viejo) usa el de la etapa', () => {
    expect(pctDeEtapa({ stage: 'ROI_DETECTION', stage_pct: null }, 'ROI_DETECTION', ETAPAS)).toBe(67)
  })

  it('los pesos de la barra general suman 100', () => {
    expect(Object.values(PESO_ETAPA).reduce((a, b) => a + b, 0)).toBe(100)
  })
})

describe('estadísticos y formato', () => {
  it('media y varianza muestral (n − 1), como en Resultados', () => {
    const inmovilidad = [88, 104, 76, 118]
    expect(mean(inmovilidad)).toBe(96.5)
    expect(variance(inmovilidad)).toBe(337)
    expect(r1(Math.sqrt(variance(inmovilidad)))).toBe('18.4')
  })

  it('segundos a min:seg', () => {
    expect(fmt(0)).toBe('0:00')
    expect(fmt(171)).toBe('2:51')
    expect(fmt(300)).toBe('5:00')
  })
})

describe('fechas', () => {
  it('fecha corta sin depender de la zona horaria', () => {
    expect(fechaCorta('2026-02-18')).toBe('18 feb 2026')
    expect(fechaCorta('2025-12-01')).toBe('1 dic 2025')
  })

  it('rango del mismo mes, de meses distintos y del mismo día', () => {
    expect(fechaRango('2026-02-18', '2026-02-19')).toBe('18–19 feb 2026')
    expect(fechaRango('2026-02-28', '2026-03-01')).toBe('28 feb 2026 – 1 mar 2026')
    expect(fechaRango('2026-10-04', '2026-10-04')).toBe('4 oct 2026')
  })
})

describe('texto', () => {
  it('búsqueda sin acentos ni mayúsculas', () => {
    expect(norm('Valeriána')).toBe(norm('valeriana'))
  })

  it('correos de cualquier dominio (D-46)', () => {
    expect(esCorreo('mrivera@ipn.mx')).toBe(true)
    expect(esCorreo('  MRivera@IPN.MX ')).toBe(true)
    expect(esCorreo('alguien@gmail.com')).toBe(true)
    expect(esCorreo('sin arroba')).toBe(false)
    expect(esCorreo('sin@dominio')).toBe(false)
  })
})

describe('resultados', () => {
  it('la primera tanda con el Día 2 analizado, o null si no hay', () => {
    const exp = { grupos: [
      { id: 'G-01', tandas: [{ letra: 'A', estado: 'QUEUED' }] },
      { id: 'G-02', tandas: [{ letra: 'A', estado: 'FAILED' }, { letra: 'B', estado: 'DONE' }] },
    ] }
    expect(primeraTandaLista(exp)).toEqual({ gid: 'G-02', letra: 'B' })
    expect(primeraTandaLista({ grupos: [] })).toBeNull()
    expect(enlaceResultados('EXP-2026-02', 'G-02', 'B')).toBe('/experimentos/EXP-2026-02/resultados?grupo=G-02&tanda=B')
  })
})
