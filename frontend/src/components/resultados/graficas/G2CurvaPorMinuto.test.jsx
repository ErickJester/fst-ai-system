// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import axe from 'axe-core'
import G2CurvaPorMinuto from './G2CurvaPorMinuto'

// Datos fijos con la forma de GET /results/charts (porMinuto de cada grupo).
const CONDUCTAS = ['nado activo', 'inmovilidad', 'escalamiento', 'conducta activa', 'sin ver']

const est = (media, de, n) => ({ n, media, de, varianza: de == null ? null : de * de, cv: null })

function porConducta(base) {
  return Object.fromEntries(CONDUCTAS.map((c) => [c, base[c] ?? est(null, null, 0)]))
}

// Una fila de porMinuto: n animales con segundos en el minuto; `incompleto` y `seg` marcan un minuto corto.
function fila(minuto, n, media, de, { incompleto = false, seg = 60 } = {}) {
  if (n === 0) {
    return { minuto, n: 0, segundosMin: null, segundosMax: null, incompleto: false, porConducta: porConducta({}) }
  }
  return {
    minuto, n, segundosMin: seg, segundosMax: seg, incompleto,
    porConducta: porConducta({ inmovilidad: est(media, de, n), 'nado activo': est(media + 5, de, n) }),
  }
}

function grupo(over) {
  return {
    idGrupo: 1, etiqueta: 'Control', tipo: 'control', tratamiento: 'Solución salina', n: 4, nTotal: 4, faltantes: [],
    nivelMixto: false, duracionMediaS: 300, especimenes: [], porConducta: porConducta({}), porMinuto: null, porNivel: null, ...over,
  }
}

const control = grupo({
  porMinuto: [
    fila(1, 4, 40, 10), fila(2, 4, 50, 8), fila(3, 4, 60, 12), fila(4, 4, 55, 9),
    fila(5, 1, 30, null, { incompleto: true, seg: 30 }),
  ],
})
const referencia = grupo({
  idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 3, nTotal: 4,
  faltantes: [{ letra: 'B', nCilindros: 1, estado: 'en pausa' }],
  porMinuto: [fila(1, 1, 35, null), fila(2, 3, 45, 6), fila(3, 0), fila(4, 3, 50, 7), fila(5, 3, 52, 7)],
})
const dosis = grupo({
  idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental',
  porMinuto: [fila(1, 4, 20, 4), fila(2, 4, 25, 4), fila(3, 4, 30, 5), fila(4, 4, 28, 5), fila(5, 4, 26, 5)],
})

const tresGrupos = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [control, referencia, dosis] }

const sinDatos = grupo({ idGrupo: 4, etiqueta: 'Experimental B', tipo: 'tratamiento experimental', n: 0, nTotal: 12, porMinuto: [1, 2, 3, 4, 5].map((m) => fila(m, 0)) })
const mixto = grupo({ idGrupo: 5, etiqueta: 'Dosis mixta', tipo: 'tratamiento experimental', nivelMixto: true, porMinuto: null, porNivel: [] })
const conSinDatosYMixto = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [control, sinDatos, mixto] }

const sinDesviacion = grupo({
  idGrupo: 6, etiqueta: 'Sin DE', tipo: 'tratamiento experimental',
  porMinuto: [1, 2, 3, 4, 5].map((m) => fila(m, 1, 30 + m, null)),
})
const datosSinDe = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [sinDesviacion] }

afterEach(() => {
  cleanup()
})

describe('G2 · curva por minuto', () => {
  it('con tres grupos dibuja una línea por grupo y los pone en la leyenda', () => {
    const { container } = render(<G2CurvaPorMinuto datos={tresGrupos} prefijo="g2a" />)
    expect(container.querySelector('[data-linea="Control"]')).not.toBeNull()
    expect(container.querySelector('[data-linea="Referencia"]')).not.toBeNull()
    expect(container.querySelector('[data-linea="Dosis 1"]')).not.toBeNull()
    expect(screen.getAllByText('Dosis 1').length).toBeGreaterThan(0)
  })

  it('un minuto sin animales (n = 0) no tiene marcador y corta la línea', () => {
    const { container } = render(<G2CurvaPorMinuto datos={tresGrupos} prefijo="g2b" />)
    expect(container.querySelector('[data-punto="Referencia|3"]')).toBeNull()
    expect(container.querySelector('[data-punto="Referencia|2"]')).not.toBeNull()
    expect(container.querySelectorAll('[data-linea="Referencia"]').length).toBe(2)
  })

  it('con de null no hay banda y la tabla dice «± no aplica»', () => {
    const { container } = render(<G2CurvaPorMinuto datos={datosSinDe} prefijo="g2c" />)
    expect(container.querySelector('[data-banda="Sin DE"]')).toBeNull()
    expect(container.querySelector('[data-linea="Sin DE"]')).not.toBeNull()
    expect(screen.getByRole('cell', { name: '31,0 s ± no aplica' })).toBeTruthy()
  })

  it('un minuto incompleto se marca en el eje, en el marcador y en la tabla, sin texto sobre el dibujo', () => {
    const { container } = render(<G2CurvaPorMinuto datos={tresGrupos} prefijo="g2d" />)
    expect(screen.getAllByText('incompleto').length).toBeGreaterThan(0)
    expect(container.querySelector('[data-punto="Control|5"]').getAttribute('data-incompleto')).toBe('si')
    expect(container.querySelector('[data-punto="Control|4"]').getAttribute('data-incompleto')).toBeNull()
    expect(container.querySelectorAll('svg text').length).toBeGreaterThan(0)
    expect([...container.querySelectorAll('svg text')].some((t) => t.textContent.includes('de 60'))).toBe(false)
    expect(screen.getByRole('cell', { name: '30,0 s ± no aplica · incompleto, 30 s de 60' })).toBeTruthy()
  })

  it('un grupo sin datos y uno de nivel mixto no se dibujan y se señalan con su nota', () => {
    const { container } = render(<G2CurvaPorMinuto datos={conSinDatosYMixto} prefijo="g2e" />)
    expect(container.querySelectorAll('[data-linea]').length).toBe(1)
    expect(container.querySelector('[data-linea="Control"]')).not.toBeNull()
    expect(screen.getByText('Experimental B: sin datos.')).toBeTruthy()
    expect(screen.getByText('Dosis mixta: Sus tandas se analizaron con distinto detalle y no se pueden promediar juntas. Míralas por separado en el detalle.')).toBeTruthy()
  })

  it('el selector empieza en inmovilidad y cambia la conducta de la tabla', () => {
    render(<G2CurvaPorMinuto datos={tresGrupos} prefijo="g2f" />)
    const selector = screen.getByRole('combobox')
    expect(selector.value).toBe('inmovilidad')
    fireEvent.change(selector, { target: { value: 'nado activo' } })
    expect(selector.value).toBe('nado activo')
    expect(screen.getByText(/Conducta: nado activo\./)).toBeTruthy()
  })

  it('sin fallos de axe-core con tres grupos, uno sin datos y uno mixto', async () => {
    const { container } = render(<G2CurvaPorMinuto datos={conSinDatosYMixto} prefijo="g2g" />)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})
