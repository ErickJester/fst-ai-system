import { describe, it, expect } from 'vitest'
import { porcentajesDeFila, SEGUNDOS_ANALIZADOS } from './fst'

describe('porcentajes de una fila de la tabla por espécimen (M12)', () => {
  it('el análisis guarda 300 s, aunque el video dure más', () => {
    expect(SEGUNDOS_ANALIZADOS).toBe(300)
  })

  it('un video de 360 s con 300 s guardados: la fila suma 100 % (no 300/360)', () => {
    // Nado 60 s, inmovilidad 60, escalamiento 60, conducta activa 60, sin ver 60: 300 s en total.
    const pcts = porcentajesDeFila([60, 60, 60, 60, 60])
    expect(pcts).toEqual([20, 20, 20, 20, 20])
    expect(pcts.reduce((a, b) => a + b, 0)).toBe(100)
  })

  it('con división que no es exacta, las filas siguen sumando 100', () => {
    const pcts = porcentajesDeFila([100, 100, 100])
    expect(pcts).toEqual([34, 33, 33])
    expect(pcts.reduce((a, b) => a + b, 0)).toBe(100)
  })

  it('una fila sin segundos da ceros', () => {
    expect(porcentajesDeFila([0, 0, 0])).toEqual([0, 0, 0])
  })
})
