// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, cleanup, within } from '@testing-library/react'
import axe from 'axe-core'
import G4ApiladaPorGrupo from './G4ApiladaPorGrupo'

const CONDUCTAS = ['nado activo', 'inmovilidad', 'escalamiento', 'conducta activa', 'sin ver']

const est = (media, n = 3) => ({ n, media, de: media == null ? null : 2, varianza: media == null ? null : 4, cv: media == null ? null : 0.02, valoresS: [] })

// Medias por categoría que suman la duración analizada del grupo.
function porConducta(medias) {
  return Object.fromEntries(CONDUCTAS.map((c) => [c, est(medias[c] ?? null, medias[c] == null ? 0 : 3)]))
}

const MEDIAS_CONTROL = { 'nado activo': 100, 'inmovilidad': 120, 'escalamiento': 30, 'conducta activa': 20, 'sin ver': 30 }

function grupo(over) {
  return {
    idGrupo: 1, etiqueta: 'Control', tipo: 'control', tratamiento: 'Solución salina', n: 3, nTotal: 3, faltantes: [], nivelMixto: false,
    duracionMediaS: 300, especimenes: [], porConducta: porConducta(MEDIAS_CONTROL), porMinuto: null, porNivel: null, ...over,
  }
}

const datos = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [grupo({})] }

afterEach(() => {
  cleanup()
})

describe('G4 · barra apilada por grupo', () => {
  it('con tres grupos muestra una columna por grupo, en el orden de la respuesta, y la duración en la tabla', () => {
    const tres = {
      ...datos,
      grupos: [
        grupo({}),
        grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', porConducta: porConducta({ ...MEDIAS_CONTROL, inmovilidad: 150 }), duracionMediaS: 300 }),
        grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', porConducta: porConducta({ ...MEDIAS_CONTROL, escalamiento: 60 }), duracionMediaS: 300 }),
      ],
    }
    render(<G4ApiladaPorGrupo datos={tres} prefijo="t4" />)
    const filas = within(screen.getByRole('table')).getAllByRole('row').slice(1).map((f) => f.cells[0].textContent)
    expect(filas).toEqual(['Control', 'Referencia', 'Dosis 1'])
    expect(screen.getAllByText('300,0 s').length).toBeGreaterThan(0)
  })

  it('usa las cinco categorías: la tabla tiene una columna para cada una, incluida «Sin ver»', () => {
    render(<G4ApiladaPorGrupo datos={datos} prefijo="t4" />)
    const cabeceras = within(screen.getByRole('table')).getAllByRole('columnheader').map((c) => c.textContent)
    expect(cabeceras).toEqual(['Grupo', 'n', 'Nado activo', 'Inmovilidad', 'Escalamiento', 'Conducta activa', 'Sin ver', 'Duración analizada'])
  })

  it('la escala sigue a la duración real: con video corto (240 s) el eje llega a 240, no a 300', () => {
    const corto = { ...datos, grupos: [grupo({ porConducta: porConducta({ 'nado activo': 80, 'inmovilidad': 100, 'escalamiento': 20, 'conducta activa': 20, 'sin ver': 20 }), duracionMediaS: 240 })] }
    const { container } = render(<G4ApiladaPorGrupo datos={corto} prefijo="t4" />)
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).toContain('240')
    expect(textos).not.toContain('300')
    expect(screen.getAllByText('240,0 s').length).toBeGreaterThan(0)
  })

  it('la suma de los segmentos se dibuja: cada categoría con media lleva su número cuando cabe', () => {
    const { container } = render(<G4ApiladaPorGrupo datos={datos} prefijo="t4" />)
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).toContain('120,0')
    expect(textos).toContain('100,0')
    expect(textos).toContain('300,0 s')
  })

  it('un segmento con media null no se dibuja y su celda dice «sin datos», nunca 0', () => {
    const sinActiva = { ...datos, grupos: [grupo({ porConducta: porConducta({ ...MEDIAS_CONTROL, 'conducta activa': null }) })] }
    render(<G4ApiladaPorGrupo datos={sinActiva} prefijo="t4" />)
    expect(within(screen.getByRole('table')).getByRole('cell', { name: 'sin datos' })).toBeTruthy()
  })

  it('un grupo sin datos y uno con nivel mixto se señalan y no se dibujan', () => {
    const mezcla = {
      ...datos,
      grupos: [
        grupo({}),
        grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 0, nTotal: 3, porConducta: porConducta({}), duracionMediaS: null }),
        grupo({
          idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', nivelMixto: true, porConducta: null, duracionMediaS: null, porMinuto: null,
          porNivel: [{ letra: 'A', nivelClasif: 'preciso', n: 2, porConducta: porConducta({ ...MEDIAS_CONTROL }) }],
        }),
      ],
    }
    const { container } = render(<G4ApiladaPorGrupo datos={mezcla} prefijo="t4" />)
    expect(screen.getAllByText('sin datos').length).toBeGreaterThan(0)
    expect(screen.getAllByText('nivel mixto').length).toBeGreaterThan(0)
    expect(screen.getByText(/Dosis 1: Mezcla niveles de clasificación; no se promedia junto\./)).toBeTruthy()
    expect(screen.getByText('Tanda A · preciso')).toBeTruthy()
    // Cinco segmentos del control (la conducta activa lleva además su rayado) y ninguna barra para los otros dos grupos.
    const rectangulos = container.querySelector('svg[role=img]').querySelectorAll('rect')
    expect(rectangulos.length).toBe(6)
  })

  it('con pendientes muestra n de cuántos y la nota de la tanda pendiente', () => {
    const pendiente = { ...datos, grupos: [grupo({ n: 2, nTotal: 3, faltantes: [{ letra: 'B', nCilindros: 1, estado: 'procesando' }] })] }
    render(<G4ApiladaPorGrupo datos={pendiente} prefijo="t4" />)
    expect(screen.getAllByText('n = 2 de 3').length).toBeGreaterThan(0)
    expect(screen.getByText('Control: 1 especímenes pendientes (Tanda B procesando).')).toBeTruthy()
  })

  it('trae las dos notas obligatorias y la descarga con su nombre', () => {
    render(<G4ApiladaPorGrupo datos={datos} prefijo="t4" />)
    expect(screen.getByText(/no indica si una diferencia es significativa/)).toBeTruthy()
    expect(screen.getByText(/salida del modelo; su exactitud todavía no está medida/)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Descargar imagen' })).toBeTruthy()
  })

  it('sin fallos de axe-core con tres grupos, uno sin datos y uno mixto', async () => {
    const mezcla = {
      ...datos,
      grupos: [
        grupo({}),
        grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 0, nTotal: 3, porConducta: porConducta({}), duracionMediaS: null }),
        grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', nivelMixto: true, porConducta: null, duracionMediaS: null, porNivel: [] }),
      ],
    }
    const { container } = render(<G4ApiladaPorGrupo datos={mezcla} prefijo="t4" />)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})
