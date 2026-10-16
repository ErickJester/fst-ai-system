import { describe, it, expect } from 'vitest'
import { ESTADO, ETAPA, TIPO_GRUPO, SESION, ROL, NOTIFICACION, CONDUCTA, NIVEL, ordinalDeLetra, letraDeOrdinal } from './contrato'

describe('equivalencias guía ↔ pantallas (PARA_VANESA_API.md §7)', () => {
  it('cada tabla va y vuelve sin perder valores', () => {
    for (const t of [ESTADO, ETAPA, TIPO_GRUPO, SESION, ROL, NOTIFICACION, CONDUCTA, NIVEL]) {
      for (const [g, f] of Object.entries(t.aFront)) expect(t.aGuia[f]).toBe(g)
      expect(Object.keys(t.aGuia)).toHaveLength(Object.keys(t.aFront).length)
    }
  })

  it('valores de la guía', () => {
    expect(ESTADO.aFront['en espera del Día 2']).toBe('ESPERA_DIA2')
    expect(ESTADO.aGuia.FAILED).toBe('error')
    expect(SESION.aGuia.DAY2).toBe('Dia 2')
    expect(ROL.aFront.Administrador).toBe('ADMIN')
    expect(CONDUCTA.aFront['conducta activa']).toBe('activa')
  })

  it('la letra de la tanda es su ordinal (A = 1)', () => {
    expect(ordinalDeLetra('A')).toBe(1)
    expect(ordinalDeLetra('C')).toBe(3)
    expect(letraDeOrdinal(2)).toBe('B')
  })
})
