// Contrato de GET /experiments/:clave/results/charts con datos de ejemplo (ESPECIFICACION.md): el orden de
// los grupos, los estadísticos que quedan null cuando no existen y que las cinco categorías suman la
// duración de cada espécimen (G4 depende de ello).
import { describe, it, expect } from 'vitest'
import { getGraficas } from './results'

const CONDUCTAS = ['nado activo', 'inmovilidad', 'escalamiento', 'conducta activa', 'sin ver']

describe('getGraficas (datos de ejemplo)', () => {
  it('trae todos los grupos del Día 2: control, referencia y luego experimentales, en su orden', async () => {
    const d = await getGraficas('7')
    expect(d.dia).toBe(2)
    expect(d.conductas).toEqual(CONDUCTAS)
    expect(d.minutos).toEqual([1, 2, 3, 4, 5])
    expect(d.grupos.map((g) => [g.idGrupo, g.tipo])).toEqual([[1, 'control'], [2, 'referencia'], [3, 'tratamiento experimental'], [4, 'tratamiento experimental']])
  })

  it('un grupo con nivel mixto no trae estadísticos y da sus números por tanda', async () => {
    const referencia = (await getGraficas('7')).grupos[1]
    expect(referencia.nivelMixto).toBe(true)
    expect(referencia.porConducta).toBeNull()
    expect(referencia.porMinuto).toBeNull()
    expect(referencia.porNivel.map((r) => r.letra)).toEqual(['A', 'B'])
  })

  it('un grupo sin especímenes con Día 2 va con n = 0 y sin números', async () => {
    const experimentalB = (await getGraficas('7')).grupos[3]
    expect(experimentalB.n).toBe(0)
    expect(experimentalB.duracionMediaS).toBeNull()
    expect(experimentalB.porConducta['nado activo']).toMatchObject({ n: 0, media: null, de: null, varianza: null, cv: null, valoresS: [] })
  })

  it('las tandas pendientes van en faltantes, con su estado', async () => {
    const control = (await getGraficas('7')).grupos[0]
    expect(control.faltantes).toEqual([{ letra: 'B', nCilindros: expect.any(Number), estado: 'en pausa' }])
  })

  it('en cada espécimen, las cinco categorías suman su duración analizada', async () => {
    const control = (await getGraficas('7')).grupos[0]
    control.especimenes.forEach((e, i) => {
      const suma = CONDUCTAS.reduce((a, c) => a + control.porConducta[c].valoresS[i], 0)
      expect(suma).toBe(e.duracionS)
    })
  })

  it('por minuto trae siempre cinco filas; un minuto con menos de 60 s se marca como incompleto', async () => {
    const control = (await getGraficas('7')).grupos[0]
    expect(control.porMinuto).toHaveLength(5)
    expect(control.porMinuto.every((m) => m.incompleto === false)).toBe(true)
    expect(control.porMinuto[0]).toMatchObject({ minuto: 1, n: control.n, segundosMin: 60, segundosMax: 60 })
  })

  it('la desviación y la varianza muestrales siguen n − 1: con dos especímenes existen', async () => {
    const control = (await getGraficas('7')).grupos[0]
    const nado = control.porConducta['nado activo']
    const valores = nado.valoresS
    const media = valores.reduce((a, v) => a + v, 0) / valores.length
    const varianza = valores.reduce((a, v) => a + (v - media) ** 2, 0) / (valores.length - 1)
    expect(nado.varianza).toBeCloseTo(Math.round(varianza * 10) / 10, 6)
  })
})
