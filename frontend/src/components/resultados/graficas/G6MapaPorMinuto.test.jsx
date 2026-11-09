// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import axe from 'axe-core'
import G6MapaPorMinuto from './G6MapaPorMinuto'

// Datos fijos con la forma de GET /results/charts (porMinuto de cada grupo).
const CONDUCTAS = ['nado activo', 'inmovilidad', 'escalamiento', 'conducta activa', 'sin ver']

const est = (media, de, n) => ({ n, media, de, varianza: de == null ? null : de * de, cv: null })

function porConducta(base) {
  return Object.fromEntries(CONDUCTAS.map((c) => [c, base[c] ?? est(null, null, 0)]))
}

// Una fila de porMinuto: n especímenes con segundos en el minuto; `incompleto` y `seg` marcan un minuto corto.
function fila(minuto, n, media, { incompleto = false, seg = 60 } = {}) {
  if (n === 0) {
    return { minuto, n: 0, segundosMin: null, segundosMax: null, incompleto: false, porConducta: porConducta({}) }
  }
  return {
    minuto, n, segundosMin: seg, segundosMax: seg, incompleto,
    porConducta: porConducta({ inmovilidad: est(media, null, n) }),
  }
}

function grupo(over) {
  return {
    idGrupo: 1, etiqueta: 'Control', tipo: 'control', tratamiento: 'Solución salina', n: 4, nTotal: 4, faltantes: [],
    nivelMixto: false, duracionMediaS: 300, especimenes: [], porConducta: porConducta({}), porMinuto: null, porNivel: null, ...over,
  }
}

const control = grupo({
  porMinuto: [fila(1, 4, 5), fila(2, 4, 10), fila(3, 4, 60), fila(4, 4, 35), fila(5, 1, 25, { incompleto: true, seg: 30 })],
})
const referencia = grupo({
  idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 3, nTotal: 4,
  faltantes: [{ letra: 'B', nCilindros: 1, estado: 'en pausa' }],
  porMinuto: [fila(1, 1, 15), fila(2, 3, 45), fila(3, 0), fila(4, 3, 50), fila(5, 3, 52)],
})
const dosis = grupo({
  idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental',
  porMinuto: [fila(1, 4, 20), fila(2, 4, 25), fila(3, 4, 30), fila(4, 4, 28), fila(5, 4, 26)],
})

const tresGrupos = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [control, referencia, dosis] }

const sinDatos = grupo({ idGrupo: 4, etiqueta: 'Experimental B', tipo: 'tratamiento experimental', n: 0, nTotal: 12, porMinuto: [1, 2, 3, 4, 5].map((m) => fila(m, 0)) })
const mixto = grupo({ idGrupo: 5, etiqueta: 'Dosis mixta', tipo: 'tratamiento experimental', nivelMixto: true, porMinuto: null, porNivel: [] })
const conSinDatosYMixto = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [control, sinDatos, mixto] }

// Límites de los rangos: 9,9 (0–10), 10 (10–20), 0 (0–10), 20 (20–30) y 60 (50–60).
const limites = grupo({
  idGrupo: 7, etiqueta: 'Límites', tipo: 'tratamiento experimental',
  porMinuto: [fila(1, 4, 9.9), fila(2, 4, 10), fila(3, 4, 0), fila(4, 4, 20), fila(5, 4, 60)],
})
const datosLimites = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [limites] }

afterEach(() => {
  cleanup()
})

describe('G6 · mapa por minuto', () => {
  it('con tres grupos pone la media en cada celda y el rango que le toca', () => {
    const { container } = render(<G6MapaPorMinuto datos={tresGrupos} prefijo="g6a" />)
    expect(container.querySelector('[data-celda="Control|1"]').getAttribute('data-rango')).toBe('0')
    expect(container.querySelector('[data-celda="Control|4"] text').textContent).toBe('35,0')
    expect(container.querySelector('[data-celda="Dosis 1|3"]').getAttribute('data-rango')).toBe('3')
    expect(container.querySelector('[data-celda="Referencia|2"]').getAttribute('data-rango')).toBe('4')
  })

  it('un minuto sin especímenes (n = 0) es un rayado gris sin número', () => {
    const { container } = render(<G6MapaPorMinuto datos={tresGrupos} prefijo="g6b" />)
    const celda = container.querySelector('[data-celda="Referencia|3"]')
    expect(celda.getAttribute('data-estado')).toBe('sindatos')
    expect(celda.querySelectorAll('text').length).toBe(0)
  })

  it('un grupo mixto no lleva celdas: su fila dice «tandas no comparables» y su nota', () => {
    const { container } = render(<G6MapaPorMinuto datos={conSinDatosYMixto} prefijo="g6c" />)
    expect(container.querySelector('[data-celda^="Dosis mixta"]')).toBeNull()
    expect(container.querySelector('[data-mixto="Dosis mixta"]').textContent).toBe('tandas no comparables')
    expect(screen.getByText('Dosis mixta: Sus tandas se analizaron con distinto detalle y no se pueden promediar juntas. Míralas por separado en el detalle.')).toBeTruthy()
  })

  it('un grupo sin datos aparece con su nota y todas sus celdas sin datos', () => {
    const { container } = render(<G6MapaPorMinuto datos={conSinDatosYMixto} prefijo="g6d" />)
    expect(screen.getByText('Experimental B: sin datos.')).toBeTruthy()
    for (let m = 1; m <= 5; m++) {
      expect(container.querySelector(`[data-celda="Experimental B|${m}"]`).getAttribute('data-estado')).toBe('sindatos')
    }
  })

  it('un minuto incompleto tiene borde discontinuo, valor en cursiva y «inc.», y su celda lo dice en la tabla', () => {
    const { container } = render(<G6MapaPorMinuto datos={tresGrupos} prefijo="g6e" />)
    const celda = container.querySelector('[data-celda="Control|5"]')
    expect(celda.getAttribute('data-incompleto')).toBe('si')
    expect(celda.querySelector('text').getAttribute('font-style')).toBe('italic')
    expect(celda.textContent).toContain('inc.')
    expect(screen.getByRole('cell', { name: '25,0 s · incompleto, 30 s de 60' })).toBeTruthy()
  })

  it('los límites 0, 10 y 60 caen en su rango: 10 en 10–20 y 60 en 50–60', () => {
    const { container } = render(<G6MapaPorMinuto datos={datosLimites} prefijo="g6f" />)
    const rango = (m) => container.querySelector(`[data-celda="Límites|${m}"]`).getAttribute('data-rango')
    expect(rango(1)).toBe('0') // 9,9
    expect(rango(2)).toBe('1') // 10
    expect(rango(3)).toBe('0') // 0
    expect(rango(4)).toBe('2') // 20
    expect(rango(5)).toBe('5') // 60
    expect(screen.getByText('9,9')).toBeTruthy()
    expect(screen.getByText('60,0')).toBeTruthy()
  })

  it('el número va siempre en tinta sobre un fondo claro que lo separa del patrón, sin contorno', () => {
    const { container } = render(<G6MapaPorMinuto datos={tresGrupos} prefijo="g6g" />)
    for (const celda of ['Control|1', 'Control|3']) {
      const texto = container.querySelector(`[data-celda="${celda}"] text`)
      expect(texto.getAttribute('fill')).toBe('var(--color-text)')
      expect(texto.getAttribute('stroke')).toBeNull()
      expect(texto.getAttribute('paint-order')).toBeNull()
      expect(texto.previousElementSibling.tagName.toLowerCase()).toBe('rect')
      expect(texto.previousElementSibling.getAttribute('fill')).toBe('var(--color-bg)')
    }
  })

  it('la leyenda muestra los seis rangos y «sin datos»', () => {
    render(<G6MapaPorMinuto datos={tresGrupos} prefijo="g6h" />)
    for (const etiqueta of ['0–10 s', '10–20 s', '20–30 s', '30–40 s', '40–50 s', '50–60 s', 'sin datos']) {
      expect(screen.getAllByText(etiqueta).length).toBeGreaterThan(0)
    }
  })

  it('el selector empieza en inmovilidad y cambia la conducta de la tabla', () => {
    render(<G6MapaPorMinuto datos={tresGrupos} prefijo="g6i" />)
    const selector = screen.getByRole('combobox')
    expect(selector.value).toBe('inmovilidad')
    fireEvent.change(selector, { target: { value: 'nado activo' } })
    expect(selector.value).toBe('nado activo')
    expect(screen.getByText(/Conducta: nado activo\./)).toBeTruthy()
  })

  it('sin fallos de axe-core con tres grupos, uno sin datos y uno mixto', async () => {
    const { container } = render(<G6MapaPorMinuto datos={conSinDatosYMixto} prefijo="g6j" />)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})
