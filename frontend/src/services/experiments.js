import api from './api'
import { usaEjemplo } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { usuarioDeLaSesion, esAdmin } from './mocks/sesion'
import { ESTADO, ETAPA, TIPO_GRUPO, ordinalDeLetra, letraDeOrdinal } from '../lib/contrato'
import { getStatus } from './queue'
import { norm } from '../lib/fst'

// Datos de ejemplo, salvo que esta parte ya esté conectada (VITE_API_REAL).
const USE_MOCKS = usaEjemplo('experimentos')

// Fecha local de dentro de n días, como 'AAAA-MM-DD'.
function enDias(n) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

// Aviso de vencimiento (R-05): si los videos se borran en 7 días o menos, la fecha exacta.
const avisoDe = (e) => (!e.videosBorrados && e.diasRetencion != null && e.diasRetencion <= 7 ? { fechaBorrado: enDias(e.diasRetencion) } : null)

// «M. Rivera»: inicial del nombre y primer apellido de quien creó el experimento.
const PARTICULAS = ['de', 'del', 'la', 'las', 'los', 'y']
function primerApellido(apellidos = '') {
  const p = apellidos.split(' ').filter(Boolean)
  let i = 0
  while (i < p.length - 1 && PARTICULAS.includes(p[i].toLowerCase())) i++
  return p.slice(0, i + 1).join(' ')
}
const responsableDe = (c) => (c ? c.nombre[0] + '. ' + primerApellido(c.apellidos) : '')

// Fila de la lista (§6.1, más los campos de más de data.js) → forma de las pantallas. La
// clave de las rutas es idExperimento.
export const deFila = (e) => ({
  clave: String(e.idExperimento),
  titulo: e.nombre,
  fecha_inicio: e.fecha,
  n_grupos: e.nGrupos,
  estado: ESTADO.aFront[e.estado],
  aviso_vencimiento: e.avisoVencimiento ? { fecha_borrado: e.avisoVencimiento.fechaBorrado } : null,
  responsable: responsableDe(e.creador),
  creador_id: e.creador?.idInstitucional != null ? String(e.creador.idInstitucional) : null,
  n_especimenes: e.nEspecimenes ?? null,
  tratamientos: e.tratamientos || [],
  videos_dia2_listos: e.videosDia2?.analizados ?? 0,
  videos_dia2_cargados: e.videosDia2?.cargados ?? 0,
  videos_dia2_total: e.videosDia2?.total ?? 0,
  retencion_dias: e.diasRetencion ?? null,
  videos_borrados: !!e.videosBorrados,
  con_detalle: e.conDetalle ?? true, // con el backend, todos tienen detalle
})

// Experimentos por página: los mismos 20 que da el servidor.
export const POR_PAGINA = 20

// El resumen de la lista (§6.1, GET /experiments → resumen) → forma de la pantalla: los conteos con la clave de cada estado,
// y los avisos de vencimiento con la clave del experimento.
export const deResumen = (r) => ({
  total: r?.total ?? 0,
  por_estado: Object.fromEntries(Object.entries(r?.porEstado || {}).map(([estado, n]) => [ESTADO.aFront[estado], n])),
  anios: r?.anios || [],
  tratamientos: r?.tratamientos || [],
  avisos: (r?.avisos || []).map((a) => ({ clave: String(a.idExperimento), titulo: a.nombre, fecha_borrado: a.fechaBorrado })),
})

const mock = {
  // Lo mismo que el servidor, en memoria: filtra por estado (el texto de la guía), fecha (AAAA o AAAA-MM-DD), tratamiento
  // completo y q (un pedazo del nombre o de algún tratamiento, sin acentos ni mayúsculas), pagina de 20 en 20 y arma el
  // resumen de TODOS los experimentos.
  listar({ pagina = 1, estado, fecha, tratamiento, q } = {}) {
    const todos = db.experiments.map((e) => ({ ...e, avisoVencimiento: avisoDe(e) }))
    const tratamientoBuscado = tratamiento ? norm(tratamiento.trim()) : null
    const texto = q && q.trim() ? norm(q.trim()) : null
    const filtrados = todos
      .filter((e) => !estado || e.estado === estado)
      .filter((e) => !fecha || (fecha.length === 4 ? e.fecha.startsWith(fecha + '-') : e.fecha === fecha))
      .filter((e) => !tratamientoBuscado || e.tratamientos.some((t) => norm(t) === tratamientoBuscado))
      .filter((e) => !texto || norm(e.nombre).includes(texto) || e.tratamientos.some((t) => norm(t).includes(texto)))
    const porEstado = {}
    const tratamientos = new Map()
    for (const e of todos) {
      porEstado[e.estado] = (porEstado[e.estado] || 0) + 1
      e.tratamientos.forEach((t) => { if (!tratamientos.has(norm(t))) tratamientos.set(norm(t), t) })
    }
    return reply({
      pagina,
      total: filtrados.length,
      items: filtrados.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA),
      resumen: {
        total: todos.length,
        porEstado,
        anios: [...new Set(todos.map((e) => e.fecha.slice(0, 4)))].sort().reverse(),
        tratamientos: [...tratamientos.values()].sort((a, b) => a.localeCompare(b, 'es')),
        avisos: todos.filter((e) => e.avisoVencimiento).map((e) => ({ idExperimento: e.idExperimento, nombre: e.nombre, fechaBorrado: e.avisoVencimiento.fechaBorrado })),
      },
    })
  },
  crear({ nombre, fecha, notas }) {
    if (db.experiments.some((e) => e.nombre.trim().toLowerCase() === nombre.trim().toLowerCase())) {
      return fail(409, 'Ya existe un experimento con ese nombre.')
    }
    const idExperimento = Math.max(0, ...db.experiments.map((e) => e.idExperimento)) + 1
    const u = usuarioDeLaSesion()
    const creador = u ? { idInstitucional: u.idInstitucional, nombre: u.nombre, apellidos: u.apellidos } : null
    db.experiments.unshift({
      idExperimento, nombre, fecha, nGrupos: 0, estado: 'sin videos', creador, nEspecimenes: 0, tratamientos: [],
      videosDia2: { analizados: 0, cargados: 0, total: 0 }, diasRetencion: null, videosBorrados: false, conDetalle: true,
    })
    // El detalle sigue con la forma anterior hasta la siguiente fase (con /status).
    db.experimentDetail[idExperimento] = { clave: idExperimento, titulo: nombre, fecha_inicio: fecha, responsable: responsableDe(creador), creador_id: creador?.idInstitucional ?? null, notas, grupos: [] }
    return reply({ idExperimento })
  },
}

// GET /experiments?pagina=&estado=&fecha=&tratamiento=&q= → { pagina, total, items, resumen } (§6.1): una página de 20 con los
// filtros aplicados en el servidor, y el resumen de TODOS los experimentos (conteos por estado, años, tratamientos y avisos)
// para que la pantalla no necesite la lista completa. estado va como en el frontend (ESTADO_EXPERIMENTO) y anio como fecha; los
// filtros vacíos no se mandan.
export async function listExperiments({ estado, anio, tratamiento, q, pagina = 1 } = {}) {
  const params = { pagina }
  if (estado) params.estado = ESTADO.aGuia[estado]
  if (anio) params.fecha = anio
  if (tratamiento) params.tratamiento = tratamiento
  if (q && q.trim()) params.q = q.trim()
  const d = USE_MOCKS ? await mock.listar(params) : (await api.get('/experiments', { params })).data
  return { pagina: d.pagina, total: d.total, porPagina: POR_PAGINA, items: d.items.map(deFila), resumen: deResumen(d.resumen) }
}

// Todos los experimentos, juntando las páginas: para quien necesita la lista completa y no una página (los análisis en curso).
export async function listAllExperiments() {
  const items = []
  for (let pagina = 1; ; pagina++) {
    const d = await listExperiments({ pagina })
    items.push(...d.items)
    if (!d.items.length || items.length >= d.total) break
  }
  return items
}

// POST /experiments { nombre, fecha, notas } → { idExperimento }. Paso 1, «Datos generales»
// (nombre único, fecha y notas opcionales, §7). El servidor pone como creador a la cuenta de
// la sesión. 409 si ya existe un experimento con ese nombre. La respuesta no está en la guía:
// por confirmar con Erick.
export async function createExperiment({ titulo, fecha_inicio, notas }) {
  const datos = { nombre: titulo, fecha: fecha_inicio, notas }
  const d = USE_MOCKS ? await mock.crear(datos) : (await api.post('/experiments', datos)).data
  return { clave: String(d.idExperimento) }
}

// Detalle de los datos de ejemplo con la forma de §6.1: grupos → tandas → videos. data.js
// guarda lo que guardaría la base; aquí se arma la respuesta. De más sobre la guía: creador y
// subidoPor (PARA_VANESA_API.md §4) y la fecha de cada video (propuesta, para la tanda).
function detalleDe(clave) {
  const exp = db.experimentDetail[clave]
  const fila = db.experiments.find((e) => String(e.idExperimento) === String(clave))
  const video = (g, t, n, extra) => ({ idVideo: g.id + '/' + t.letra + '/' + n, sesion: 'Dia ' + n, duracion: 300, subidoPor: t.subido_por ?? null, ...extra })
  return {
    idExperimento: exp.clave, nombre: exp.titulo, fecha: exp.fecha_inicio, notas: exp.notas ?? '',
    estado: fila?.estado ?? 'sin videos', creador: fila?.creador ?? null,
    grupos: exp.grupos.map((g) => ({
      idGrupo: g.id, etiqueta: g.nombre, tipo: TIPO_GRUPO.aGuia[g.tipo], tratamiento: g.tratamiento,
      tandas: g.tandas.map((t) => ({
        idTanda: g.id + '/' + t.letra, ordinal: ordinalDeLetra(t.letra), letra: t.letra, nCilindros: t.n_cilindros,
        videos: [
          t.dia1 && video(g, t, 1, { videoDisponible: true, idAnalisis: null, fecha: t.fecha_dia1 }),
          t.estado !== 'SIN_VIDEO' && video(g, t, 2, { videoDisponible: t.video_disponible !== false, idAnalisis: t.id_analisis, fecha: t.fecha_dia2 }),
        ].filter(Boolean),
      })),
    })),
  }
}

// Detalle (§6.1) más el estado de sus análisis (/status) → forma de las pantallas. El estado,
// la etapa y el avance de cada tanda salen del análisis de su video del Día 2.
export function deDetalle(d, analisis = []) {
  const porId = new Map(analisis.map((a) => [a.idAnalisis, a]))
  return {
    clave: String(d.idExperimento),
    titulo: d.nombre,
    fecha_inicio: d.fecha,
    notas: d.notas ?? '',
    estado: ESTADO.aFront[d.estado],
    responsable: responsableDe(d.creador),
    creador_id: d.creador?.idInstitucional != null ? String(d.creador.idInstitucional) : null,
    grupos: d.grupos.map((g) => {
      let desde = 1
      const tandas = [...g.tandas].sort((a, b) => a.ordinal - b.ordinal).map((t) => {
        const v1 = t.videos.find((v) => v.sesion === 'Dia 1')
        const v2 = t.videos.find((v) => v.sesion === 'Dia 2')
        const a = v2?.idAnalisis != null ? porId.get(v2.idAnalisis) : null
        const estado = v2 ? ESTADO.aFront[a?.estado] || 'QUEUED' : 'SIN_VIDEO'
        const etapa = a?.etapa ? ETAPA.aFront[a.etapa] : null
        const x = {
          letra: t.letra || letraDeOrdinal(t.ordinal),
          id_tanda: t.idTanda ?? null, // sin backend (datos de ejemplo) no hay id: no se puede eliminar la tanda
          desde,
          n_cilindros: t.nCilindros,
          duracion_s: v2?.duracion ? Math.round(v2.duracion) : null,
          fecha_dia1: v1?.fecha ?? v2?.fecha ?? d.fecha,
          fecha_dia2: v2?.fecha ?? v1?.fecha ?? d.fecha,
          estado,
          dia1: v1 ? { cargado: true } : null,
          progreso: estado === 'RUNNING' && etapa ? { etapa, pct: a.porcentaje } : null,
          error: estado === 'FAILED' ? { etapa } : null,
          id_analisis: v2?.idAnalisis ?? null,
          subido_por: (v2?.subidoPor ?? v1?.subidoPor) != null ? String(v2?.subidoPor ?? v1?.subidoPor) : null,
          analisis_fecha: estado === 'DONE' ? a?.fechaAnalisis ?? null : null,
          ...(v2?.videoDisponible === false && { video_disponible: false }),
        }
        desde += t.nCilindros
        return x
      })
      return {
        id: String(g.idGrupo), nombre: g.etiqueta, tipo: TIPO_GRUPO.aFront[g.tipo], tratamiento: g.tratamiento,
        n_especimenes: g.tandas.reduce((n, t) => n + t.nCilindros, 0), tandas,
      }
    }),
  }
}

// GET /experiments/:clave (§6.1) y GET /experiments/:clave/status → datos generales + grupos →
// tandas, con el estado de cada análisis (pantallas 2c y 2d).
export async function getExperiment(clave) {
  return (await detalleConStatus(clave)).exp
}

// El detalle ya traducido y los análisis de /status tal como llegan (para Progreso).
export async function detalleConStatus(clave) {
  const [d, s] = await Promise.all([
    USE_MOCKS
      ? (db.experimentDetail[clave] ? reply(detalleDe(clave)) : fail(404, 'No existe ese experimento.'))
      : api.get(`/experiments/${clave}`).then((r) => r.data),
    getStatus(clave),
  ])
  return { exp: deDetalle(d, s.analisis), analisis: s.analisis }
}

// DELETE /experiments/:clave → borra el experimento con todo lo suyo. Según D-63 no pide
// nombre ni contraseña: la pantalla solo muestra una confirmación. Solo lo borra quien lo
// creó o un Administrador (403); se rechaza con 409 si algún análisis está procesando o en
// pausa, y los que están en cola salen de la cola (R-08, D-87).
export async function deleteExperiment(clave) {
  if (USE_MOCKS) {
    const i = db.experiments.findIndex((e) => String(e.idExperimento) === String(clave))
    if (i < 0) return fail(404, 'No existe ese experimento.')
    const u = usuarioDeLaSesion()
    if (!u || (!esAdmin(u) && db.experiments[i].creador?.idInstitucional !== u.idInstitucional)) {
      return fail(403, 'Solo quien creó el experimento o un Administrador puede eliminarlo.')
    }
    const tandas = db.experimentDetail[clave]?.grupos.flatMap((g) => g.tandas) || []
    if (tandas.some((t) => t.estado === 'RUNNING' || t.estado === 'PAUSED')) {
      return fail(409, 'No se puede eliminar mientras un análisis está procesando o en pausa. Inténtalo cuando termine.')
    }
    quitar(db.queue, (j) => String(j.clave) === String(clave))
    db.queue.forEach((j, n) => { j.posicion = n + 1 })
    quitar(db.failedJobs, (j) => String(j.clave) === String(clave))
    db.experiments.splice(i, 1)
    delete db.experimentDetail[clave]
    return reply({ ok: true })
  }
  return (await api.delete(`/experiments/${clave}`)).data
}

// DELETE /experiments/:clave/tandas/:idTanda → elimina una tanda con sus videos (Día 1 y Día 2) y resultados, para
// dejar libre su letra si se subió mal. 403 si no es quien creó el experimento ni Administrador; 409 si su análisis
// está procesando o en pausa; 204 sin cuerpo. Sin backend no hay id de tanda y el botón no sale.
export async function deleteTanda(clave, idTanda) {
  if (USE_MOCKS) return reply({ ok: true })
  await api.delete(`/experiments/${clave}/tandas/${idTanda}`)
}

// Quita de la lista, sin reemplazarla, los elementos que cumplen la condición.
function quitar(lista, cond) {
  for (let k = lista.length - 1; k >= 0; k--) if (cond(lista[k])) lista.splice(k, 1)
}
