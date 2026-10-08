import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { simular } from './mocks/simulador'
import { mean, variance } from '../lib/fst'

// GET /experiments/:clave/groups/:gid/batches/:letra/results?day= → por cilindro (s),
// línea de tiempo, nivel y calidad del análisis (2f)
export async function getBatchResults(clave, gid, letra, dia = 'DAY2') {
  if (USE_MOCKS) {
    simular()
    const r = resultadosDeEjemplo(clave, gid, letra, dia)
    return r ? reply(r) : fail(404, 'Esta tanda todavía no tiene resultados.')
  }
  return (await api.get(`/experiments/${clave}/groups/${gid}/batches/${letra}/results`, { params: { day: dia } })).data
}

// Resultados de una tanda terminada con datos de ejemplo. Las tandas que terminan en la
// simulación no traen los suyos: reutilizan los de la Tanda A de Referencia con su grupo,
// letra y cilindros, para poder recorrerlas.
function resultadosDeEjemplo(clave, gid, letra, dia) {
  const exp = db.experimentDetail[clave]
  const g = exp?.grupos.find((x) => x.id === gid)
  const t = g?.tandas.find((x) => x.letra === letra)
  if (!t || t.estado !== 'DONE' || dia !== 'DAY2') return null
  const propios = db.batchResults[[clave, gid, letra, dia].join('/')]
  if (propios) return propios
  const base = db.batchResults['EXP-2026-02/G-02/A/DAY2']
  return {
    ...base,
    experimento: { clave: exp.clave, titulo: exp.titulo },
    grupo: { id: g.id, nombre: g.nombre, tipo: g.tipo, tratamiento: g.tratamiento },
    letra,
    cilindros: base.cilindros.slice(0, t.n_cilindros),
  }
}

const media = (a) => Math.round(mean(a) * 10) / 10
const de = (a) => (a.length > 1 ? Math.round(Math.sqrt(variance(a)) * 10) / 10 : 0)

// Inmovilidad por grupo, Día 2: solo entran los cilindros de tandas con el Día 2
// analizado. Si un grupo mezcla niveles de clasificación, va por tanda (por_nivel).
function compararGrupos(clave) {
  const exp = db.experimentDetail[clave]
  if (!exp) return []
  return exp.grupos.map((g) => {
    const listas = g.tandas
      .map((t) => ({ t, r: resultadosDeEjemplo(clave, g.id, t.letra, 'DAY2') }))
      .filter((x) => x.r)
    const valores = listas.flatMap((x) => x.r.cilindros.map((c) => c.inmovilidad_s))
    const pendientes = g.tandas
      .filter((t) => t.estado !== 'DONE')
      .map((t) => ({ tanda: t.letra, n: t.n_cilindros, estado: t.estado === 'SIN_VIDEO' ? null : t.estado }))
    const fila = { grupo: g.nombre + ' · ' + g.tratamiento, tipo: g.tipo, n: valores.length, n_total: g.n_especimenes, pendientes }
    if (new Set(listas.map((x) => x.r.nivel)).size > 1) {
      fila.por_nivel = listas.map(({ t, r }) => {
        const v = r.cilindros.map((c) => c.inmovilidad_s)
        return { tanda: t.letra, nivel: r.nivel, n: v.length, media_s: media(v), de_s: de(v) }
      })
    } else {
      fila.media_s = valores.length ? media(valores) : null
      fila.de_s = valores.length ? de(valores) : null
    }
    return fila
  })
}

// GET /experiments/:clave/group-comparison → inmovilidad media ± DE por grupo, Día 2
export async function getGroupComparison(clave) {
  if (USE_MOCKS) {
    simular()
    return reply(compararGrupos(clave))
  }
  return (await api.get(`/experiments/${clave}/group-comparison`)).data
}
