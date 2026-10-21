import api from './api'
import { usaEjemplo } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { segundosDe, totalesDe, minutosDe, CONDUCTAS, DURACION_S } from './mocks/segundos'
import { getExperiment } from './experiments'
import { CONDUCTA, ESTADO, NIVEL, TIPO_GRUPO, ordinalDeLetra } from '../lib/contrato'
import { mean, variance, nombreDeContentDisposition } from '../lib/fst'

// Datos de ejemplo, salvo que esta parte ya esté conectada (VITE_API_REAL).
const USE_MOCKS = usaEjemplo('resultados')

// Resultados con las rutas de la guía (§6, §6.1), todas del experimento completo:
// /results (totales por espécimen), /results/byminute (lo mismo con sus minutos),
// /results/groups (comparación entre grupos) y /results/blocks (bloques de 5 s). El filtro
// por grupo o tanda se hace aquí, en el navegador (D-93). Cada espécimen trae su tanda como
// «Control · A» y su ordinal; de más sobre la guía (propuesto) puede traer idGrupo, que es lo
// que se usa para filtrar si viene.

// ── datos de ejemplo con la forma de la guía ───────────────────────────────

// Resultados de una tanda terminada con datos de ejemplo (la forma que guarda data.js). Las
// tandas que terminan en la simulación no traen los suyos: reutilizan los de la Tanda A de
// Referencia con su grupo, letra y especímenes, para poder recorrerlas. Con correcciones
// guardadas, los totales salen de los segundos revisados (R-14).
function resultadosDeEjemplo(clave, gid, letra) {
  const exp = db.experimentDetail[clave]
  const g = exp?.grupos.find((x) => x.id === gid)
  const t = g?.tandas.find((x) => x.letra === letra)
  if (!t || t.estado !== 'DONE') return null
  const propios = db.batchResults[[clave, gid, letra, 'DAY2'].join('/')]
  const base = db.batchResults['7/2/A/DAY2']
  const r = propios || { ...base, especimenes: base.especimenes.slice(0, t.n_cilindros) }
  const revision = db.revisiones[t.id_analisis]
  const especimenes = revision
    ? r.especimenes.map((e) => ({ numeroCilindro: e.numeroCilindro, ...totalesDe(segundosDe(t.id_analisis, e)) }))
    : r.especimenes
  const nivel = revision ? (especimenes.some((e) => e.activa_s > 0) ? 'AGRUPADO' : 'PRECISO') : r.nivel
  return {
    g, t, nivel,
    especimenes: especimenes.map((e) => ({
      ...e,
      revisado: !!revision?.[e.numeroCilindro],
      minutos: minutosDe(segundosDe(t.id_analisis, r.especimenes.find((x) => x.numeroCilindro === e.numeroCilindro))),
    })),
  }
}

const tandasTerminadas = (clave) => (db.experimentDetail[clave]?.grupos || []).flatMap((g) => g.tandas.map((t) => resultadosDeEjemplo(clave, g.id, t.letra)).filter(Boolean))

// Un espécimen de ejemplo con la forma de /results (y, con minutos, de /results/byminute).
function especimenGuia({ g, t, nivel }, e, conMinutos) {
  const totales = Object.fromEntries(CONDUCTAS.map((c) => [CONDUCTA.aGuia[c], { segundos: e[c + '_s'], porcentaje: Math.round((e[c + '_s'] / DURACION_S) * 1000) / 10 }]))
  return {
    idGrupo: g.id, tanda: g.nombre + ' · ' + t.letra, ordinal: ordinalDeLetra(t.letra), numeroCilindro: e.numeroCilindro,
    nivelClasif: NIVEL.aGuia[nivel], revisado: e.revisado, totales,
    ...(conMinutos && {
      minutos: e.minutos.map((tramos, i) => ({
        minuto: i + 1,
        ...Object.fromEntries(CONDUCTAS.map((c) => [CONDUCTA.aGuia[c], tramos.filter(([x]) => x === c).reduce((a, [, s]) => a + s, 0)])),
      })),
    }),
  }
}

const mock = {
  results: (clave, conMinutos) => {
    if (!db.experimentDetail[clave]) return fail(404, 'No existe ese experimento.')
    return reply({ especimenes: tandasTerminadas(clave).flatMap((r) => r.especimenes.map((e) => especimenGuia(r, e, conMinutos))) })
  },
}

// GET /experiments/:clave/results o .../results/byminute. Las tandas de una pantalla se piden
// a la vez: mientras una petición sigue en curso, las demás la comparten.
const enCurso = new Map()
function pedir(clave, ruta) {
  const k = clave + ' ' + ruta
  if (!enCurso.has(k)) {
    const p = USE_MOCKS ? mock.results(clave, ruta === 'results/byminute') : api.get(`/experiments/${clave}/${ruta}`).then((r) => r.data)
    enCurso.set(k, p)
    p.then(() => enCurso.delete(k), () => enCurso.delete(k))
  }
  return enCurso.get(k)
}

// El espécimen de la guía pertenece a esa tanda: por idGrupo si viene; si no, por el texto
// «Control · A» que manda la guía. El ordinal siempre tiene que coincidir.
const deLaTanda = (g, letra) => (e) =>
  (e.idGrupo != null ? String(e.idGrupo) === String(g.id) : e.tanda === g.nombre + ' · ' + letra) && (e.ordinal == null || e.ordinal === ordinalDeLetra(letra))

const segundosPorConducta = (totales) => Object.fromEntries(CONDUCTAS.map((c) => [c + '_s', totales?.[CONDUCTA.aGuia[c]]?.segundos ?? 0]))

// Un minuto de /results/byminute → tramos [conducta, segundos] (forma de las pantallas).
const tramosDe = (m) => CONDUCTAS.filter((c) => m[CONDUCTA.aGuia[c]] > 0).map((c) => [c, m[CONDUCTA.aGuia[c]]])

// Resultados de una tanda (2f): por espécimen (s), desglose por minuto, nivel, si se revisó, y
// lo que la revisión necesita (id_analisis, subido_por, video_disponible). Junta el detalle,
// /results y /results/byminute.
// «tiempoAnalisis» de un espécimen de /results ({ totalSegundos, equipo }, o null si no hay registro) → lo que usa la
// pantalla: { total_s, equipo }. Los especímenes de una tanda comparten análisis, así que basta el primero.
export function tiempoAnalisisFront(especimen) {
  const t = especimen?.tiempoAnalisis
  return t && t.totalSegundos != null ? { total_s: t.totalSegundos, equipo: t.equipo ?? null } : null
}

export async function getBatchResults(clave, gid, letra) {
  const [exp, res, min] = await Promise.all([getExperiment(clave), pedir(clave, 'results'), pedir(clave, 'results/byminute')])
  const g = exp.grupos.find((x) => x.id === gid)
  const t = g?.tandas.find((x) => x.letra === letra)
  if (!t || t.estado !== 'DONE') return fail(404, 'Esta tanda todavía no tiene resultados.')
  const mios = res.especimenes.filter(deLaTanda(g, letra)).sort((a, b) => a.numeroCilindro - b.numeroCilindro)
  const lineas_tiempo = min.especimenes.filter(deLaTanda(g, letra)).sort((a, b) => a.numeroCilindro - b.numeroCilindro)
    .map((e) => ({ numeroCilindro: e.numeroCilindro, minutos: [...(e.minutos || [])].sort((a, b) => a.minuto - b.minuto).map(tramosDe) }))
  return {
    experimento: { clave: exp.clave, titulo: exp.titulo },
    grupo: { id: g.id, nombre: g.nombre, tipo: g.tipo, tratamiento: g.tratamiento },
    letra,
    dia: 'DAY2',
    // Duración del video del Día 2 según el detalle (300 s en un video normal de 5 min).
    duracion_s: t.duracion_s || DURACION_S,
    duracion_analizada_s: t.duracion_s || DURACION_S,
    analizado_en: t.analisis_fecha || exp.fecha_inicio,
    nivel: mios.some((e) => e.nivelClasif === 'agrupado') ? 'AGRUPADO' : 'PRECISO',
    revisado: mios.some((e) => e.revisado),
    tiempo_analisis: tiempoAnalisisFront(mios[0]),
    nombre_video: mios[0]?.nombreVideo ?? null,    // como lo subió el usuario; null en los videos anteriores
    especimenes: mios.map((e) => ({ numeroCilindro: e.numeroCilindro, ...segundosPorConducta(e.totales) })),
    linea_tiempo: lineas_tiempo[0],
    lineas_tiempo,
    id_analisis: t.id_analisis,
    subido_por: t.subido_por,
    video_disponible: t.video_disponible !== false,
  }
}

// ── Comparación entre grupos (RF-21) ───────────────────────────────────────
const media = (a) => Math.round(mean(a) * 10) / 10
const de = (a) => (a.length > 1 ? Math.round(Math.sqrt(variance(a)) * 10) / 10 : 0)

// Media ± DE de cada conducta entre los especímenes dados (promedio, no suma: el tamaño del
// grupo no distorsiona), con los nombres de la base.
const promediosDe = (especimenes) => Object.fromEntries(CONDUCTAS.map((c) => {
  const v = especimenes.map((e) => e[c + '_s'])
  return [CONDUCTA.aGuia[c], v.length ? { media: media(v), de: de(v) } : { media: null, de: null }]
}))

// GET /experiments/:clave/results/groups con datos de ejemplo. La guía dice qué trae (el
// promedio por conducta de cada grupo, los especímenes con dato faltante y un aviso si mezcla
// niveles), no la forma; propuesta: [{ idGrupo, etiqueta, tipo, tratamiento, n, nTotal,
// faltantes: [{ letra, nCilindros, estado }], nivelMixto, promedios, porNivel }].
function compararGrupos(clave) {
  const exp = db.experimentDetail[clave]
  return exp.grupos.map((g) => {
    const listas = g.tandas.map((t) => resultadosDeEjemplo(clave, g.id, t.letra)).filter(Boolean)
    const especimenes = listas.flatMap((r) => r.especimenes)
    const nivelMixto = new Set(listas.map((r) => r.nivel)).size > 1
    return {
      idGrupo: g.id, etiqueta: g.nombre, tipo: TIPO_GRUPO.aGuia[g.tipo], tratamiento: g.tratamiento,
      n: especimenes.length, nTotal: g.n_especimenes,
      faltantes: g.tandas.filter((t) => t.estado !== 'DONE').map((t) => ({ letra: t.letra, nCilindros: t.n_cilindros, estado: t.estado === 'SIN_VIDEO' ? null : ESTADO.aGuia[t.estado] })),
      nivelMixto,
      promedios: nivelMixto ? null : promediosDe(especimenes),
      porNivel: nivelMixto ? listas.map((r) => ({ letra: r.t.letra, nivelClasif: NIVEL.aGuia[r.nivel], n: r.especimenes.length, promedios: promediosDe(r.especimenes) })) : null,
    }
  })
}

// Promedios de la guía → { nado: { media_s, de_s }, … } (forma de las pantallas).
const porConducta = (p) => Object.fromEntries(CONDUCTAS.map((c) => [c, { media_s: p?.[CONDUCTA.aGuia[c]]?.media ?? null, de_s: p?.[CONDUCTA.aGuia[c]]?.de ?? null }]))

// GET /experiments/:clave/results/groups → por grupo, media ± DE de cada conducta con el Día 2;
// los especímenes sin Día 2 analizado se señalan como pendientes. Si un grupo mezcla niveles
// de clasificación, va por tanda (por_nivel) y se avisa.
export async function getGroupComparison(clave) {
  const d = USE_MOCKS
    ? (db.experimentDetail[clave] ? await reply(compararGrupos(clave)) : await fail(404, 'No existe ese experimento.'))
    : (await api.get(`/experiments/${clave}/results/groups`)).data
  return d.map((g) => ({
    grupo: g.etiqueta + ' · ' + g.tratamiento,
    tipo: TIPO_GRUPO.aFront[g.tipo],
    n: g.n,
    n_total: g.nTotal,
    pendientes: (g.faltantes || []).map((f) => ({ tanda: f.letra, n: f.nCilindros, estado: f.estado ? ESTADO.aFront[f.estado] : null })),
    ...(g.nivelMixto && g.porNivel
      ? { por_nivel: g.porNivel.map((x) => ({ tanda: x.letra, nivel: NIVEL.aFront[x.nivelClasif], n: x.n, por_conducta: porConducta(x.promedios) })) }
      : { por_conducta: porConducta(g.promedios) }),
  }))
}

// ── Bloques de 5 s (D-53) ───────────────────────────────────────────────────
// GET /experiments/:clave/results/blocks → por espécimen, sus 60 bloques con la conducta
// mayoritaria y sus cinco segundos: { inicio (segundo, desde 1), conducta, matiz, segundos }
// (conductas con el nombre de la base). Los calcula el backend al pedirlos, por mayoría sobre
// la conducta de cada segundo (regla en PARA_ERICK.md). Se filtran los de la tanda. Sin
// backend no hay bloques: responde null y la pantalla explica.
export async function getBlocks(clave, gid, letra) {
  if (USE_MOCKS) return reply(null)
  let exp, d
  try {
    [exp, d] = await Promise.all([getExperiment(clave), pedir(clave, 'results/blocks')])
  } catch (e) {
    if (e?.response?.status === 404) return null // el backend aún no los calcula
    throw e
  }
  const g = exp.grupos.find((x) => x.id === gid)
  if (!g) return null
  const aFront = (c) => (c ? CONDUCTA.aFront[c] : null)
  return {
    especimenes: d.especimenes.filter(deLaTanda(g, letra)).map((e) => ({
      numeroCilindro: e.numeroCilindro,
      bloques: e.bloques.map((b) => ({ ...b, conducta: aFront(b.conducta), segundos: (b.segundos || []).map(aFront) })),
    })),
  }
}

// ── Reportes (RF-22) ────────────────────────────────────────────────────────
// GET /experiments/:clave/report/:formato (csv | xlsx | pdf) → el archivo, tal cual. Lo genera
// el backend: si no existe lo crea, si es más viejo que el último análisis lo regenera y si
// sigue vigente devuelve el guardado. CSV y XLSX reciben el alcance de lo que se ve (D-93):
// ?grupo= (grupo completo) o además &tanda= (una tanda). El frontend solo lo descarga. Sin
// backend no hay archivo: responde null y la pantalla explica qué hará el botón.
// GET /experiments/:clave/report/segundos?grupo=&tanda= (solo modo desarrollador): la salida por segundo del modelo TAL CUAL
// (un CSV por tanda, o un ZIP con el de cada tanda si solo va el grupo). Es la salida CRUDA del modelo: no trae las
// correcciones de la revisión. Se descarga como blob con el nombre que manda el servidor (Content-Disposition).
// Sin backend no hay archivo: responde null.
export async function getSegundosModelo(clave, alcance) {
  if (USE_MOCKS) return reply(null)
  const res = await api.get(`/experiments/${clave}/report/segundos`, { params: alcance, responseType: 'blob' })
  const zip = !alcance.tanda
  return {
    archivo: res.data,
    nombre: nombreDeContentDisposition(res.headers?.['content-disposition']) || (zip ? 'segundos.zip' : 'segundos.csv'),
    tipo: zip ? 'application/zip' : 'text/csv',
  }
}

export async function getReporte(clave, formato, alcance = null) {
  if (USE_MOCKS) return reply(null)
  return (await api.get(`/experiments/${clave}/report/${formato}`, { params: alcance || undefined, responseType: 'blob' })).data
}
