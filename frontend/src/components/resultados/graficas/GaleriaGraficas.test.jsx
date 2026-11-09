// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, cleanup, within } from '@testing-library/react'
import axe from 'axe-core'
import GaleriaGraficas from './GaleriaGraficas'

// Datos con la forma de GET /results/charts (ESPECIFICACION.md): grupos con estadísticos y datos por minuto.
const CONDUCTAS = ['nado activo', 'inmovilidad', 'escalamiento', 'conducta activa', 'sin ver']

const est = (media, de, n = 2, valoresS) => ({ n, media, de, varianza: de == null ? null : de * de, cv: de == null || !media ? null : Math.round((de / media) * 1000) / 1000, ...(valoresS && { valoresS }) })

function porConducta(base) {
  return Object.fromEntries(CONDUCTAS.map((c) => [c, base[c] ?? est(null, null, 0, [])]))
}

function grupo(over) {
  return {
    idGrupo: 1, etiqueta: 'Control', tipo: 'control', tratamiento: 'Solución salina', n: 2, nTotal: 2, faltantes: [], nivelMixto: false,
    duracionMediaS: 300, especimenes: [], porConducta: porConducta({ 'inmovilidad': est(120, 10, 2, [110, 130]), 'conducta activa': est(4, 2, 2, [2, 6]) }),
    porMinuto: null, porNivel: null, ...over,
  }
}

const datosDeUnGrupo = { dia: 2, conductas: CONDUCTAS, minutos: [1, 2, 3, 4, 5], grupos: [grupo({})] }

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  localStorage.clear()
})

describe('GaleriaGraficas · detalles y avisos', () => {
  it('las notas y la tabla van en «Ver números y detalles», cerrado; el aviso de un grupo sin datos queda visible', () => {
    const datos = { ...datosDeUnGrupo, grupos: [grupo({}), grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 0, nTotal: 2, porConducta: porConducta({}) })] }
    const { container } = render(<GaleriaGraficas datos={datos} />)
    const detalles = container.querySelector('details')
    expect(detalles.open).toBe(false)
    expect(detalles.querySelector('summary').textContent).toBe('Ver números y detalles')
    expect(detalles.querySelector('table')).toBeTruthy()
    const aviso = screen.getByText('Referencia: sin datos.')
    expect(detalles.contains(aviso)).toBe(false)
  })
})

describe('GaleriaGraficas · G1', () => {
  it('con un grupo dibuja su barra, su n y la media ± DE de la conducta elegida', () => {
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    expect(screen.getByRole('heading', { name: '¿Cuánto hizo cada grupo?' })).toBeTruthy()
    expect(screen.getAllByText('120,0 ± 10,0 s').length).toBeGreaterThan(0)
    expect(screen.getAllByText('2 animales').length).toBeGreaterThan(0)
  })

  it('con dos y con tres grupos dibuja una etiqueta por grupo, en el orden de la respuesta', () => {
    const dos = { ...datosDeUnGrupo, grupos: [grupo({}), grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia' })] }
    const { unmount } = render(<GaleriaGraficas datos={dos} />)
    const tabla = screen.getByRole('table')
    expect(within(tabla).getAllByRole('row').length).toBe(3) // cabecera y dos grupos
    unmount()
    const tres = { ...datosDeUnGrupo, grupos: [grupo({}), grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia' }), grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental' })] }
    render(<GaleriaGraficas datos={tres} />)
    const filas = within(screen.getByRole('table')).getAllByRole('row').slice(1).map((f) => f.cells[0].textContent)
    expect(filas).toEqual(['Control', 'Referencia', 'Dosis 1'])
  })

  it('un grupo sin datos y uno con nivel mixto se señalan y no se dibujan promediados', () => {
    const datos = {
      ...datosDeUnGrupo,
      grupos: [
        grupo({}),
        grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 0, nTotal: 2, porConducta: porConducta({}) }),
        grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', nivelMixto: true, porConducta: null, porMinuto: null, porNivel: [] }),
      ],
    }
    render(<GaleriaGraficas datos={datos} />)
    expect(screen.getAllByText('sin datos').length).toBeGreaterThan(0)
    expect(screen.getAllByText('tandas no comparables').length).toBeGreaterThan(0)
    expect(screen.getByText(/Dosis 1: Sus tandas se analizaron con distinto detalle y no se pueden promediar juntas. Míralas por separado en el detalle\./)).toBeTruthy()
  })

  it('sin desviación no hay barra de error y el valor dice «± no aplica»', () => {
    const datos = { ...datosDeUnGrupo, grupos: [grupo({ porConducta: porConducta({ inmovilidad: est(42, null, 1, [42]) }) })] }
    render(<GaleriaGraficas datos={datos} />)
    expect(screen.getAllByText('42,0 s ± no aplica').length).toBeGreaterThan(0)
  })

  it('la conducta activa va fuera de las barras, con su nota de segundos medios', () => {
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    expect(screen.getByText(/Conducta activa, fuera de las barras \(segundos medios por animal\): Control 4,0 s\./)).toBeTruthy()
  })

  it('cada gráfica trae las dos notas obligatorias y la tabla con los mismos números', () => {
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    expect(screen.getByText(/no indica si una diferencia es significativa/)).toBeTruthy()
    expect(screen.getByText(/salida del modelo; su exactitud todavía no está medida/)).toBeTruthy()
    const celda = within(screen.getByRole('table')).getByRole('cell', { name: '120,0 ± 10,0 s' })
    expect(celda).toBeTruthy()
  })
})

describe('GaleriaGraficas · selector y teclado', () => {
  it('las seis gráficas se pueden elegir y cada una abre la suya', () => {
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    const titulos = [
      '¿Cuánto hizo cada grupo?',
      '¿Cómo cambió minuto a minuto?',
      '¿Cómo se compara cada minuto?',
      '¿En qué se fue el tiempo?',
      '¿Qué tan parecidos son los animales?',
      '¿En qué minuto cambia?',
    ]
    for (const titulo of titulos) {
      const boton = screen.getByRole('button', { name: titulo })
      expect(boton.disabled).toBe(false)
      fireEvent.click(boton)
      expect(screen.getByRole('heading', { name: titulo })).toBeTruthy()
    }
  })

  it('«Ver varias» muestra hasta tres gráficas juntas, sin repetir ids', () => {
    const { container } = render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    fireEvent.click(screen.getByRole('checkbox', { name: /Ver varias/ }))
    for (const pregunta of ['¿Cómo cambió minuto a minuto?', '¿Cómo se compara cada minuto?', '¿En qué se fue el tiempo?']) {
      fireEvent.click(screen.getByRole('button', { name: pregunta }))
    }
    expect(screen.getAllByRole('heading', { level: 3 }).length).toBe(3)
    const ids = [...container.querySelectorAll('[id]')].map((e) => e.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('las flechas no cambian la gráfica mientras el foco está en un campo de texto', () => {
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    const campo = document.createElement('select')
    document.body.appendChild(campo)
    campo.focus()
    fireEvent.keyDown(campo, { key: 'ArrowRight' })
    expect(screen.getByRole('heading', { name: '¿Cuánto hizo cada grupo?' })).toBeTruthy()
    campo.remove()
  })

  it('«Ver varias» pide marcar las gráficas y muestra el máximo', () => {
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    fireEvent.click(screen.getByRole('checkbox', { name: /Ver varias/ }))
    expect(screen.getByText(/Marca las gráficas que quieres ver juntas \(hasta 3\)\./)).toBeTruthy()
  })
})

describe('GaleriaGraficas · memoria y patrones', () => {
  it('si el navegador no deja guardar, la galería funciona igual', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('cuota llena') })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado') })
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    expect(screen.getByRole('heading', { name: '¿Cuánto hizo cada grupo?' })).toBeTruthy()
  })

  it('la elección de conducta se recuerda en el navegador', () => {
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    fireEvent.change(screen.getByLabelText('Conducta'), { target: { value: 'escalamiento' } })
    expect(JSON.parse(localStorage.getItem('fst.resultados.graficas')).conducta).toBe('escalamiento')
  })

  it('el rayado de la referencia va dentro del mismo svg, con un id propio de la gráfica', () => {
    const datos = { ...datosDeUnGrupo, grupos: [grupo({}), grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia' })] }
    const { container } = render(<GaleriaGraficas datos={datos} />)
    const svg = container.querySelector('svg')
    const patron = svg.querySelector('pattern[id$="-rayado"]')
    expect(patron).toBeTruthy()
    expect(svg.querySelector(`rect[fill="url(#${patron.id})"]`)).toBeTruthy()
  })

  it('dos gráficas en la misma página no repiten ids de patrón', () => {
    const { container } = render(<><GaleriaGraficas datos={datosDeUnGrupo} /><GaleriaGraficas datos={datosDeUnGrupo} /></>)
    const ids = [...container.querySelectorAll('pattern')].map((p) => p.id)
    expect(new Set(ids).size).toBe(ids.length)
  })
})

describe('GaleriaGraficas · accesibilidad', () => {
  it('sin fallos de axe-core con tres grupos, uno sin datos y uno mixto', async () => {
    const datos = {
      ...datosDeUnGrupo,
      grupos: [
        grupo({}),
        grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', n: 0, nTotal: 2, porConducta: porConducta({}) }),
        grupo({ idGrupo: 3, etiqueta: 'Dosis 1', tipo: 'tratamiento experimental', nivelMixto: true, porConducta: null, porMinuto: null, porNivel: [] }),
      ],
    }
    const { container } = render(<GaleriaGraficas datos={datos} />)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})

describe('G1 · etiquetas largas, escala y nivel mixto', () => {
  const nombres = ['Fluoxetina 10 mg/kg', 'Compuesto CSR-14 curva', 'Dosis intermedia', 'Dosis alta experimental', 'Dosis muy alta', 'Vehículo y control']

  it('con seis grupos de nombre largo, cada etiqueta cabe en su columna y el nombre completo queda en <title>', () => {
    const datos = { ...datosDeUnGrupo, grupos: nombres.map((etiqueta, i) => grupo({ idGrupo: i + 1, etiqueta, tipo: i === 0 ? 'control' : 'tratamiento experimental' })) }
    const { container } = render(<GaleriaGraficas datos={datos} />)
    const svg = container.querySelector('svg[role=img]')
    const titulos = [...svg.querySelectorAll('g > title')].map((t) => t.textContent).filter((t) => nombres.includes(t))
    expect(titulos).toEqual(nombres)
    // Cada renglón de etiqueta tiene como mucho el número de caracteres que cabe en la columna, y los que sobran llevan «…».
    const columna = 368 / 6
    const maxCar = Math.floor((columna - 6) / 6.3)
    // Las etiquetas de nombre de grupo son las de tamaño 11.5; las de n y de valor van en otros tamaños.
    const renglones = [...svg.querySelectorAll('text[font-size="11.5"]')].map((t) => t.textContent)
    expect(renglones.length).toBeGreaterThanOrEqual(nombres.length)
    for (const r of renglones) expect(r.length).toBeLessThanOrEqual(maxCar)
  })

  it('el partido en renglones y el recorte se calculan con el ancho de la columna', async () => {
    const { renglonesDeEtiqueta } = await import('./G1BarrasGrupos')
    expect(renglonesDeEtiqueta('Control', 10)).toEqual(['Control'])
    expect(renglonesDeEtiqueta('Fluoxetina 10 mg/kg', 11)).toEqual(['Fluoxetina', '10 mg/kg'])
    const recortado = renglonesDeEtiqueta('Dosis alta experimental de largo', 9)
    expect(recortado.every((r) => r.length <= 9)).toBe(true)
    expect(recortado[recortado.length - 1].endsWith('…')).toBe(true)
  })

  it('con nivel mixto, la tabla trae una fila por tanda con sus números; el dibujo no pone barra', () => {
    const mixto = grupo({
      idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia', nivelMixto: true, porConducta: null, porMinuto: null,
      porNivel: [
        { letra: 'A', nivelClasif: 'preciso', n: 4, porConducta: porConducta({ inmovilidad: est(100, 5, 4, [95, 100, 105, 100]) }) },
        { letra: 'B', nivelClasif: 'agrupado', n: 4, porConducta: porConducta({ inmovilidad: est(140, 8, 4, [132, 140, 148, 140]) }) },
      ],
    })
    render(<GaleriaGraficas datos={{ ...datosDeUnGrupo, grupos: [grupo({}), mixto] }} />)
    const tabla = screen.getByRole('table')
    expect(within(tabla).getByText('Tanda A · preciso')).toBeTruthy()
    expect(within(tabla).getByText('Tanda B · agrupado')).toBeTruthy()
    expect(within(tabla).getByRole('cell', { name: '140,0 ± 8,0 s' })).toBeTruthy()
  })

  it('la leyenda muestra el patrón del grupo, no solo su color', () => {
    const datos = { ...datosDeUnGrupo, grupos: [grupo({}), grupo({ idGrupo: 2, etiqueta: 'Referencia', tipo: 'referencia' })] }
    const { container } = render(<GaleriaGraficas datos={datos} />)
    const muestras = [...container.querySelectorAll('svg[aria-hidden=true]')]
    const conPatron = muestras.filter((m) => m.querySelector('rect[fill^="url(#"]'))
    expect(conPatron.length).toBe(1) // la referencia lleva rayado; el control, no
  })

  it('las flechas dan la vuelta: antes de la primera está la última, y después de la última, la primera', () => {
    render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    fireEvent.click(screen.getByRole('button', { name: 'Gráfica anterior' }))
    expect(screen.getByRole('heading', { name: '¿En qué minuto cambia?' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Gráfica siguiente' }))
    expect(screen.getByRole('heading', { name: '¿Cuánto hizo cada grupo?' })).toBeTruthy()
  })

  it('la escala es de 300 s y solo crece si un grupo la rebasa', () => {
    const { container, unmount } = render(<GaleriaGraficas datos={datosDeUnGrupo} />)
    const textos = () => [...container.querySelector('svg[role=img]').querySelectorAll('text')].map((t) => t.textContent)
    expect(textos()).toContain('300')
    expect(textos()).not.toContain('360')
    unmount()
    const alto = { ...datosDeUnGrupo, grupos: [grupo({ porConducta: porConducta({ inmovilidad: est(350, 10, 2, [340, 360]) }) })] }
    const otro = render(<GaleriaGraficas datos={alto} />)
    expect([...otro.container.querySelector('svg[role=img]').querySelectorAll('text')].map((t) => t.textContent)).toContain('360')
  })
})
