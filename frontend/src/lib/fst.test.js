import { describe, it, expect } from 'vitest'
import { mean, variance, fmt, r1, fechaCorta, fechaRango, norm, esCorreo, toCSV, primeraTandaLista, enlaceResultados, bloqueDe } from './fst'

describe('bloques de 5 s (D-53)', () => {
  const N = 'nado', I = 'inmovilidad', E = 'escalamiento', A = 'activa'
  it('gana la conducta con más segundos', () => {
    expect(bloqueDe([N, N, N, I, I])).toEqual({ conducta: N, matiz: false })
    expect(bloqueDe([N, N, E, null, null])).toEqual({ conducta: N, matiz: false }) // 2 y 1: nado
  })
  it('empate de nado activo y escalamiento: conducta activa', () => {
    expect(bloqueDe([N, N, E, E, I])).toEqual({ conducta: A, matiz: false })
  })
  it('2 de inmovilidad y 2 de nado: decide el quinto', () => {
    expect(bloqueDe([I, I, N, N, E])).toEqual({ conducta: A, matiz: false })
    expect(bloqueDe([I, I, N, N, null])).toEqual({ conducta: A, matiz: true })
    expect(bloqueDe([I, I, N, N, I])).toEqual({ conducta: I, matiz: false })
  })
  it('los cinco sin ver: conducta activa con matiz', () => {
    expect(bloqueDe([null, null, null, null, null])).toEqual({ conducta: A, matiz: true })
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

describe('CSV', () => {
  it('lleva BOM para Excel y escapa comas, comillas y saltos de línea', () => {
    const csv = toCSV([['Cilindro', 'Nota'], ['Cilindro P1', 'dijo "hola", y\nsiguió']])
    expect(csv.charCodeAt(0)).toBe(0xfeff)
    expect(csv.slice(1)).toBe('Cilindro,Nota\nCilindro P1,"dijo ""hola"", y\nsiguió"')
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
