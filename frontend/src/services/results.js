import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { segundosDe, totalesDe, minutosDe, CONDUCTAS } from './mocks/segundos'
import { mean, variance } from '../lib/fst'

// GET /experiments/:clave/groups/:gid/batches/:letra/results?day= → por espécimen (s),
// línea de tiempo, nivel y calidad del análisis (2f)
export async function getBatchResults(clave, gid, letra, dia = 'DAY2') {
  if (USE_MOCKS) {
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
  const base = db.batchResults['EXP-2026-02/G-02/A/DAY2']
  const r = propios || {
    ...base,
    experimento: { clave: exp.clave, titulo: exp.titulo },
    grupo: { id: g.id, nombre: g.nombre, tipo: g.tipo, tratamiento: g.tratamiento },
    letra,
    especimenes: base.especimenes.slice(0, t.n_cilindros),
  }
  // id_analisis y subido_por: para la revisión segundo a segundo (solo quien subió el video o
  // un Administrador). Con correcciones guardadas, los totales salen de los segundos
  // revisados (R-14) y revisado lo indica.
  // Desglose por minuto de cada espécimen (results/byminute): segundos de cada conducta en cada
  // minuto, en tramos [conducta, segundos].
  const lineas_tiempo = r.especimenes.map((e) => ({ numeroCilindro: e.numeroCilindro, minutos: minutosDe(segundosDe(t.id_analisis, e)) }))
  const conAnalisis = { ...r, id_analisis: t.id_analisis, subido_por: t.subido_por, revisado: false, lineas_tiempo }
  const revision = db.revisiones[t.id_analisis]
  if (!revision) return conAnalisis
  const especimenes = r.especimenes.map((e) => ({ numeroCilindro: e.numeroCilindro, ...totalesDe(segundosDe(t.id_analisis, e)) }))
  const primero = segundosDe(t.id_analisis, r.especimenes[0])
  return {
    ...conAnalisis, especimenes, revisado: true,
    nivel: especimenes.some((e) => e.activa_s > 0) ? 'AGRUPADO' : 'PRECISO',
    linea_tiempo: { numeroCilindro: r.especimenes[0].numeroCilindro, minutos: minutosDe(primero) },
  }
}

const media = (a) => Math.round(mean(a) * 10) / 10
const de = (a) => (a.length > 1 ? Math.round(Math.sqrt(variance(a)) * 10) / 10 : 0)

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
    return reply(compararGrupos(clave))
  }
  return (await api.get(`/experiments/${clave}/group-comparison`)).data
}

// ── Bloques de 5 s (D-53) ───────────────────────────────────────────────────
// GET /experiments/:clave/groups/:gid/batches/:letra/blocks → por espécimen, sus 60 bloques:
// { inicio (segundo, desde 1), conducta, matiz, segundos: [cinco conductas o null] }. Los
// calcula el backend al pedirlos, por mayoría sobre la conducta de cada segundo (regla en
// PARA_ERICK.md). Sin backend no hay bloques: responde null y la pantalla explica.
export async function getBlocks(clave, gid, letra) {
  if (USE_MOCKS) return reply(null)
  return (await api.get(`/experiments/${clave}/groups/${gid}/batches/${letra}/blocks`)).data
}

// ── Reportes (RF-22) ────────────────────────────────────────────────────────
// GET /experiments/:clave/report/:formato (csv | xlsx | pdf) → el archivo, tal cual. Lo genera
// el backend: si no existe lo crea, si es más viejo que el último análisis lo regenera y si
// sigue vigente devuelve el guardado. CSV y XLSX reciben el alcance de lo que se ve (D-93):
// ?grupo= (grupo completo) o además &tanda= (una tanda). El frontend solo lo descarga. Sin
// backend no hay archivo: responde null y la pantalla explica qué hará el botón.
export async function getReporte(clave, formato, alcance = null) {
  if (USE_MOCKS) return reply(null)
  return (await api.get(`/experiments/${clave}/report/${formato}`, { params: alcance || undefined, responseType: 'blob' })).data
}
