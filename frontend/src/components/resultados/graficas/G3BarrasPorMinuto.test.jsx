// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import axe from 'axe-core'
import G3BarrasPorMinuto from './G3BarrasPorMinuto'

// Datos fijos con la forma de GET /results/charts (porMinuto de cada grupo).
const CONDUCTAS = ['nado activo', 'inmovilidad', 'escalamiento', 'conducta activa', 'sin ver']

const est = (media, de, n) => ({ n, media, de, varianza: de == null ? null : de * de, cv: null })

function porConducta(base) {
  return Object.fromEntries(CONDUCTAS.map((c) => [c, base[c] ?? est(null, null, 0)]))
}

// Una fila de porMinuto: n especímenes con segundos en el minuto; `incompleto` y `seg` marcan un minuto corto.
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

describe('G3 · barras por minuto', () => {
  it('con tres grupos dibuja una barra por grupo y minuto, salvo el minuto sin especímenes', () => {
    const { container } = render(<G3BarrasPorMinuto datos={tresGrupos} prefijo="g3a" />)
    expect(container.querySelector('[data-barra="Control|1"]')).not.toBeNull()
    expect(container.querySelector('[data-barra="Dosis 1|5"]')).not.toBeNull()
    expect(container.querySelector('[data-barra="Referencia|3"]')).toBeNull()
    expect(container.querySelector('[data-barra="Referencia|2"]')).not.toBeNull()
    // Control 5 + Referencia 4 (el minuto 3 no tiene especímenes) + Dosis 1 5.
    expect(container.querySelectorAll('[data-barra]').length).toBe(14)
  })

  it('con de null no hay barra de error: la barra existe y la tabla dice «± no aplica»', () => {
    const { container } = render(<G3BarrasPorMinuto datos={tresGrupos} prefijo="g3b" />)
    expect(container.querySelector('[data-barra="Referencia|1"]')).not.toBeNull()
    expect(container.querySelector('[data-error="Referencia"]')).not.toBeNull()
    // Con DE: Control en minutos 1 a 4, Referencia en 2, 4 y 5, Dosis 1 en los cinco. Sin DE no hay barra de error.
    expect(container.querySelectorAll('[data-error]').length).toBe(12)
    expect(screen.getByRole('cell', { name: '35,0 s ± no aplica' })).toBeTruthy()
  })

  it('con todas las DE nulas no dibuja ninguna barra de error', () => {
    const { container } = render(<G3BarrasPorMinuto datos={datosSinDe} prefijo="g3c" />)
    expect(container.querySelectorAll('[data-barra]').length).toBe(5)
    expect(container.querySelectorAll('[data-error]').length).toBe(0)
  })

  it('un minuto incompleto se marca en el eje, en la barra (rayado) y en la tabla, sin texto sobre el dibujo', () => {
    const { container } = render(<G3BarrasPorMinuto datos={tresGrupos} prefijo="g3d" />)
    expect(screen.getAllByText('incompleto').length).toBeGreaterThan(0)
    expect(container.querySelector('[data-barra="Control|5"]').getAttribute('data-incompleto')).toBe('si')
    expect(container.querySelector('[data-barra="Control|4"]').getAttribute('data-incompleto')).toBeNull()
    expect([...container.querySelectorAll('svg text')].some((t) => t.textContent.includes('de 60'))).toBe(false)
    expect(screen.getByRole('cell', { name: '30,0 s ± no aplica · incompleto, 30 s de 60' })).toBeTruthy()
  })

  it('un grupo sin datos y uno de nivel mixto no se dibujan y se señalan con su nota', () => {
    const { container } = render(<G3BarrasPorMinuto datos={conSinDatosYMixto} prefijo="g3e" />)
    expect(container.querySelectorAll('[data-barra]').length).toBe(5)
    expect(container.querySelector('[data-barra="Experimental B|1"]')).toBeNull()
    expect(container.querySelector('[data-barra="Dosis mixta|1"]')).toBeNull()
    expect(screen.getByText('Experimental B: sin datos.')).toBeTruthy()
    expect(screen.getByText('Dosis mixta: Sus tandas se analizaron con distinto detalle y no se pueden promediar juntas. Míralas por separado en el detalle.')).toBeTruthy()
  })

  it('los patrones llevan el prefijo de la gráfica', () => {
    const { container } = render(<G3BarrasPorMinuto datos={tresGrupos} prefijo="g3f" />)
    expect(container.querySelector('#g3f-rayado')).not.toBeNull()
    expect(container.querySelector('#g3f-horizontal')).not.toBeNull()
  })

  it('el selector empieza en inmovilidad y cambia la conducta de la tabla', () => {
    render(<G3BarrasPorMinuto datos={tresGrupos} prefijo="g3g" />)
    const selector = screen.getByRole('combobox')
    expect(selector.value).toBe('inmovilidad')
    fireEvent.change(selector, { target: { value: 'nado activo' } })
    expect(selector.value).toBe('nado activo')
    expect(screen.getByText(/Conducta: nado activo\./)).toBeTruthy()
  })

  it('sin fallos de axe-core con tres grupos, uno sin datos y uno mixto', async () => {
    const { container } = render(<G3BarrasPorMinuto datos={conSinDatosYMixto} prefijo="g3h" />)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})
