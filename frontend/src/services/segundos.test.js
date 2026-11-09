// Contrato de GET /experiments/:clave/results/seconds con datos de ejemplo: la forma de la respuesta, «sin ver» como
// conducta null, el origen crudo y el error de una tanda sin análisis completado.
import { describe, it, expect, beforeEach } from 'vitest'
import { getSegundos } from './results'
import { reiniciarDatos } from './mocks/data'

const status = (promesa) => promesa.then(() => 'ok', (e) => e.response?.status ?? e.status ?? 'error')

beforeEach(() => reiniciarDatos())

describe('getSegundos (datos de ejemplo)', () => {
  it('trae la duración, el grupo, la letra y los segundos de cada espécimen de la tanda', async () => {
    const d = await getSegundos('7', 1, 'A')
    expect(d.duracionS).toBe(300)
    expect(d.grupo).toEqual({ id: 1, etiqueta: 'Control' })
    expect(d.letra).toBe('A')
    expect(d.especimenes).toHaveLength(4)
    expect(d.especimenes[0].segundos).toHaveLength(300)
    expect(d.especimenes[0].segundos[0]).toMatchObject({ s: 1, origen: 'maquina' })
  })

  it('«sin ver» es conducta null, y las conductas van con el nombre de la base', async () => {
    const d = await getSegundos('7', 1, 'A')
    const conductas = new Set(d.especimenes.flatMap((e) => e.segundos.map((s) => s.conducta)))
    expect(conductas.has(null)).toBe(true)
    expect([...conductas].filter((c) => c !== null).every((c) => ['nado activo', 'inmovilidad', 'escalamiento', 'conducta activa'].includes(c))).toBe(true)
  })

  it('una tanda sin análisis completado responde error 404', async () => {
    expect(await status(getSegundos('7', 1, 'B'))).toBe(404)
  })
})
