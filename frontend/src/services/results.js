import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { simular } from './mocks/simulador'
import { mean, variance, bloqueDe } from '../lib/fst'

// GET /experiments/:clave/groups/:gid/batches/:letra/results?day= → por espécimen (s),
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
// letra y especímenes, para poder recorrerlas.
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
    especimenes: base.especimenes.slice(0, t.n_cilindros),
  }
}

const media = (a) => Math.round(mean(a) * 10) / 10
const de = (a) => (a.length > 1 ? Math.round(Math.sqrt(variance(a)) * 10) / 10 : 0)
const CONDUCTAS = ['nado', 'inmovilidad', 'escalamiento', 'activa']

// Media ± DE de cada conducta entre los especímenes dados (promedio, no suma: el tamaño del
// grupo no distorsiona).
const porConducta = (especimenes) => Object.fromEntries(CONDUCTAS.map((c) => {
  const v = especimenes.map((e) => e[c + '_s'])
  return [c, v.length ? { media_s: media(v), de_s: de(v) } : { media_s: null, de_s: null }]
}))

// Comparación entre grupos con el Día 2 (RF-21): solo entran los especímenes de tandas con
// el Día 2 analizado; los demás se señalan como pendientes. Si un grupo mezcla niveles de
// clasificación (preciso y agrupado), va por tanda (por_nivel) y se avisa.
function compararGrupos(clave) {
  const exp = db.experimentDetail[clave]
  if (!exp) return []
  return exp.grupos.map((g) => {
    const listas = g.tandas
      .map((t) => ({ t, r: resultadosDeEjemplo(clave, g.id, t.letra, 'DAY2') }))
      .filter((x) => x.r)
    const especimenes = listas.flatMap((x) => x.r.especimenes)
    const pendientes = g.tandas
      .filter((t) => t.estado !== 'DONE')
      .map((t) => ({ tanda: t.letra, n: t.n_cilindros, estado: t.estado === 'SIN_VIDEO' ? null : t.estado }))
    const fila = { grupo: g.nombre + ' · ' + g.tratamiento, tipo: g.tipo, n: especimenes.length, n_total: g.n_especimenes, pendientes }
    if (new Set(listas.map((x) => x.r.nivel)).size > 1) {
      fila.por_nivel = listas.map(({ t, r }) => ({ tanda: t.letra, nivel: r.nivel, n: r.especimenes.length, por_conducta: porConducta(r.especimenes) }))
    } else {
      fila.por_conducta = porConducta(especimenes)
    }
    return fila
  })
}

// GET /experiments/:clave/group-comparison → por grupo, media ± DE de cada conducta, Día 2
export async function getGroupComparison(clave) {
  if (USE_MOCKS) {
    simular()
    return reply(compararGrupos(clave))
  }
  return (await api.get(`/experiments/${clave}/group-comparison`)).data
}

// ── Bloques de 5 s (D-53) ───────────────────────────────────────────────────
// Sin backend, la conducta de cada segundo se arma con los totales del espécimen, en tramos
// alternados como la línea de tiempo. Para mostrar el matiz, el espécimen 1 tiene unos
// segundos «no se ve» (null): datos ilustrativos, no salen de un video.
function segundosDeEjemplo(e) {
  const quedan = Object.fromEntries(CONDUCTAS.map((c) => [c, e[c + '_s']]))
  const largo = [9, 4, 7, 3, 11, 5, 2, 8, 6, 3]
  const seq = []
  let k = 0
  while (seq.length < 300 && CONDUCTAS.some((c) => quedan[c] > 0)) {
    const c = CONDUCTAS[(k + e.numeroCilindro) % 4]
    const n = Math.min(largo[k % largo.length], quedan[c], 300 - seq.length)
    k++
    if (!n) continue
    quedan[c] -= n
    for (let i = 0; i < n; i++) seq.push(c)
  }
  while (seq.length < 300) seq.push(null)
  if (e.numeroCilindro === 1) {
    for (let i = 120; i < 125; i++) seq[i] = null // un bloque entero sin ver
    seq[42] = null
    seq[43] = null
  }
  return seq
}

// GET /experiments/:clave/groups/:gid/batches/:letra/blocks → por espécimen, sus 60 bloques:
// { inicio (segundo, desde 1), conducta, matiz, segundos: [cinco conductas o null] }.
// Se calculan al pedirlos; no se guardan.
export async function getBlocks(clave, gid, letra) {
  if (USE_MOCKS) {
    simular()
    const r = resultadosDeEjemplo(clave, gid, letra, 'DAY2')
    if (!r) return fail(404, 'Esta tanda todavía no tiene resultados.')
    return reply({
      especimenes: r.especimenes.map((e) => {
        const seq = segundosDeEjemplo(e)
        const bloques = []
        for (let i = 0; i < 300; i += 5) {
          const segundos = seq.slice(i, i + 5)
          bloques.push({ inicio: i + 1, segundos, ...bloqueDe(segundos) })
        }
        return { numeroCilindro: e.numeroCilindro, bloques }
      }),
    })
  }
  return (await api.get(`/experiments/${clave}/groups/${gid}/batches/${letra}/blocks`)).data
}
