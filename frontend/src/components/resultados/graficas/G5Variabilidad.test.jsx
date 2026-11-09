// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, cleanup, within } from '@testing-library/react'
import axe from 'axe-core'
import G5Variabilidad from './G5Variabilidad'

const CONDUCTAS = ['nado activo', 'inmovilidad', 'escalamiento', 'conducta activa', 'sin ver']

// Estadísticos ya calculados por el backend: cv es fracción; varianza es DE al cuadrado.
const est = (media, cv, varianza, n = 3) => ({ n, media, de: varianza == null ? null : Math.sqrt(varianza), varianza, cv, valoresS: [] })

function porConducta(base) {
  return Object.fromEntries(CONDUCTAS.map((c) => [c, base[c] ?? est(null, null, null, 0)]))
}

function grupo(over) {
  return {
    idGrupo: 1, etiqueta: 'Control', tipo: 'control', tratamiento: 'Solución salina', n: 3, nTotal: 3, faltantes: [], nivelMixto: false,
    duracionMediaS: 300, especimenes: [], porConducta: porConducta({ inmovilidad: est(120, 0.2, 576) }), porMinuto: null, porNivel: null, ...over,
  }
}

const tresGrupos = {
  dia: 2,
  conductas: CONDUCTAS,
  minutos: [1, 2, 3, 4, 5],
  grupos: [
    grupo({}),
    grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', porConducta: porConducta({ inmovilidad: est(100, 0.5, 2500) }) }),
    grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', porConducta: porConducta({ inmovilidad: est(0, null, null) }) }),
  ],
}

const datos = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [grupo({})] }

afterEach(() => {
  cleanup()
})

// Cuatro grupos: columna de 31,6 px. CV «47 %» sobre la barra; sin varianza bajo las barras; «n/a» si no aplica.
const gEst = (cv) => est(100, cv, 1234.5)
const cuatroGrupos = {
  dia: 2,
  conductas: CONDUCTAS,
  minutos: [1, 2, 3, 4, 5],
  grupos: [
    grupo({ porConducta: porConducta({ inmovilidad: gEst(0.47) }) }),
    grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', porConducta: porConducta({ inmovilidad: gEst(0.3) }) }),
    grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', porConducta: porConducta({ inmovilidad: est(0, null, null) }) }),
    grupo({ idGrupo: 4, etiqueta: 'Dosis 2', tipo: 'tratamiento experimental', porConducta: porConducta({ inmovilidad: gEst(0.1) }) }),
  ],
}

// Seis grupos: columna de 21 px. Sin etiquetas sobre las barras; el dato queda en <title> y en la tabla.
const seisGrupos = {
  ...cuatroGrupos,
  grupos: Array.from({ length: 6 }, (_, i) => grupo({
    idGrupo: i + 1, etiqueta: `Grupo ${i + 1}`, tipo: i === 0 ? 'control' : 'tratamiento experimental',
    porConducta: porConducta({ inmovilidad: gEst(0.47) }),
  })),
}

describe('G5 · ajuste por ancho de columna', () => {
  it('con cuatro grupos: «47 %» sobre la barra, sin varianza bajo las barras y con la nota', () => {
    const { container } = render(<G5Variabilidad datos={cuatroGrupos} prefijo="t5" />)
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).toContain('47 %')
    expect(textos).not.toContain('CV 47 %')
    expect(textos).not.toContain('1234,5 s²')
    expect(textos).not.toContain('varianza')
    expect(textos).toContain('n/a')
    expect(screen.getByText('La varianza de cada grupo está en la tabla.')).toBeTruthy()
    expect(within(screen.getByRole('table')).getAllByRole('cell', { name: 'CV 47 % · varianza 1234,5 s²' }).length).toBeGreaterThan(0)
  })

  it('con cuatro grupos, un grupo sin datos se marca con «—» y uno con nivel mixto con «no comp.»', () => {
    const mezcla = {
      ...cuatroGrupos,
      grupos: [
        cuatroGrupos.grupos[0],
        grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 0, nTotal: 3, porConducta: porConducta({}), duracionMediaS: null }),
        grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', nivelMixto: true, porConducta: null, duracionMediaS: null, porNivel: [] }),
        cuatroGrupos.grupos[3],
      ],
    }
    const { container } = render(<G5Variabilidad datos={mezcla} prefijo="t5" />)
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).toContain('—')
    expect(textos).toContain('no comp.')
    expect(textos).not.toContain('sin datos')
    expect(textos).not.toContain('tandas no comparables')
  })

  it('con seis grupos (columna de 21 px) no hay etiquetas sobre las barras, pero el dato queda en <title>', () => {
    const { container } = render(<G5Variabilidad datos={seisGrupos} prefijo="t5" />)
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).not.toContain('47 %')
    expect(textos).not.toContain('CV 47 %')
    expect(textos).not.toContain('n/a')
    expect(textos).not.toContain('varianza')
    const titulos = [...container.querySelectorAll('svg title')].map((t) => t.textContent)
    expect(titulos).toContain('Grupo 1, Inmovilidad: CV 47 %; varianza 1234,5 s²')
    expect(screen.getByText('La varianza de cada grupo está en la tabla.')).toBeTruthy()
  })
})

describe('G5 · variabilidad entre animales', () => {
  it('con tres grupos (columna de 42 px) pone el CV en la barra y no la varianza, que va en la tabla', () => {
    const { container } = render(<G5Variabilidad datos={tresGrupos} prefijo="t5" />)
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).toContain('CV 20 %')
    expect(textos).toContain('CV 50 %')
    expect(textos).not.toContain('576,0 s²')
    expect(textos).not.toContain('varianza')
    expect(screen.getByText('La varianza de cada grupo está en la tabla.')).toBeTruthy()
  })

  it('con CV null (media 0 o n < 2) no dibuja barra y dice «no aplica» (columna de 42 px)', () => {
    const { container } = render(<G5Variabilidad datos={tresGrupos} prefijo="t5" />)
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).toContain('no aplica')
    const celdas = within(screen.getByRole('table')).getAllByRole('cell', { name: 'no aplica · varianza no aplica' })
    expect(celdas.length).toBeGreaterThan(0)
  })

  it('todos los CV null: no hay eje y se dice que no hay nada que dibujar', () => {
    const todosNull = { ...tresGrupos, grupos: [grupo({ porConducta: porConducta({ inmovilidad: est(0, null, null) }) })] }
    const { container } = render(<G5Variabilidad datos={todosNull} prefijo="t5" />)
    expect(screen.getByText('No hay coeficientes de variación que dibujar: no aplica.')).toBeTruthy()
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).not.toContain('0,1')
  })

  it('el título y la nota dicen que es variación entre animales, no entre analistas', () => {
    render(<G5Variabilidad datos={datos} prefijo="t5" />)
    expect(screen.getByRole('heading', { name: '¿Qué tan parecidos son los animales?' })).toBeTruthy()
    expect(screen.getByText('La variación es el coeficiente de variación (desviación estándar ÷ promedio). Mide la variación entre animales de cada grupo, no entre analistas.')).toBeTruthy()
  })

  it('un grupo sin datos y uno con nivel mixto se señalan y no se dibujan', () => {
    const mezcla = {
      ...tresGrupos,
      grupos: [
        grupo({}),
        grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 0, nTotal: 3, porConducta: porConducta({}), duracionMediaS: null }),
        grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', nivelMixto: true, porConducta: null, duracionMediaS: null, porNivel: [] }),
      ],
    }
    const { container } = render(<G5Variabilidad datos={mezcla} prefijo="t5" />)
    const textos = [...container.querySelectorAll('svg text')].map((t) => t.textContent)
    expect(textos).toContain('sin datos')
    expect(textos).toContain('tandas no comparables')
    expect(screen.getByText(/Dosis 1: Sus tandas se analizaron con distinto detalle y no se pueden promediar juntas. Míralas por separado en el detalle\./)).toBeTruthy()
  })

  it('con nivel mixto, la tabla trae una fila por tanda con su CV y su varianza', () => {
    const mixto = grupo({
      idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', nivelMixto: true, porConducta: null, duracionMediaS: null, porMinuto: null,
      porNivel: [
        { letra: 'A', nivelClasif: 'preciso', n: 4, porConducta: porConducta({ inmovilidad: est(100, 0.05, 25, 4) }) },
        { letra: 'B', nivelClasif: 'agrupado', n: 4, porConducta: porConducta({ inmovilidad: est(140, 0.057, 64, 4) }) },
      ],
    })
    render(<G5Variabilidad datos={{ ...tresGrupos, grupos: [grupo({}), mixto] }} prefijo="t5" />)
    const tabla = screen.getByRole('table')
    expect(within(tabla).getByText('Tanda A · preciso')).toBeTruthy()
    expect(within(tabla).getByText('Tanda B · agrupado')).toBeTruthy()
    expect(within(tabla).getByRole('cell', { name: 'CV 6 % · varianza 64,0 s²' })).toBeTruthy()
  })

  it('con tandas pendientes muestra n de cuántos y la nota', () => {
    const pendiente = { ...datos, grupos: [grupo({ n: 2, nTotal: 3, faltantes: [{ letra: 'B', nCilindros: 1, estado: 'en cola' }] })] }
    render(<G5Variabilidad datos={pendiente} prefijo="t5" />)
    expect(screen.getAllByText('2 de 3 animales').length).toBeGreaterThan(0)
    expect(screen.getByText('Control: 1 animal pendiente (Tanda B en cola).')).toBeTruthy()
  })

  it('trae las dos notas obligatorias y la descarga con su nombre', () => {
    render(<G5Variabilidad datos={datos} prefijo="t5" />)
    expect(screen.getByText(/no indica si una diferencia es significativa/)).toBeTruthy()
    expect(screen.getByText(/salida del modelo; su exactitud todavía no está medida/)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Descargar imagen' })).toBeTruthy()
  })

  it('sin fallos de axe-core con tres grupos, uno sin datos y uno mixto', async () => {
    const mezcla = {
      ...tresGrupos,
      grupos: [
        grupo({}),
        grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 0, nTotal: 3, porConducta: porConducta({}), duracionMediaS: null }),
        grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', nivelMixto: true, porConducta: null, duracionMediaS: null, porNivel: [] }),
      ],
    }
    const { container } = render(<G5Variabilidad datos={mezcla} prefijo="t5" />)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})
