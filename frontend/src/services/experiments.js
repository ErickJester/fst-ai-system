import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { usuarioDeLaSesion, esAdmin } from './mocks/sesion'
import { ESTADO, ETAPA, TIPO_GRUPO, ordinalDeLetra, letraDeOrdinal } from '../lib/contrato'
import { getStatus } from './queue'

// Fecha local de dentro de n días, como 'AAAA-MM-DD'.
function enDias(n) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

// Aviso de vencimiento (R-05): si los videos se borran en 7 días o menos, la fecha exacta.
const avisoDe = (e) => (!e.videosBorrados && e.diasRetencion != null && e.diasRetencion <= 7 ? { fechaBorrado: enDias(e.diasRetencion) } : null)

// «M. Rivera»: inicial del nombre y primer apellido de quien creó el experimento.
const responsableDe = (c) => (c ? c.nombre[0] + '. ' + (c.apellidos || '').split(' ')[0] : '')

// Fila de la lista (§6.1, más los campos de más de data.js) → forma de las pantallas. La
// clave de las rutas es idExperimento.
export const deFila = (e) => ({
  clave: e.idExperimento,
  titulo: e.nombre,
  fecha_inicio: e.fecha,
  n_grupos: e.nGrupos,
  estado: ESTADO.aFront[e.estado],
  aviso_vencimiento: e.avisoVencimiento ? { fecha_borrado: e.avisoVencimiento.fechaBorrado } : null,
  responsable: responsableDe(e.creador),
  creador_id: e.creador?.idInstitucional ?? null,
  n_especimenes: e.nEspecimenes ?? null,
  tratamientos: e.tratamientos || [],
  videos_dia2_listos: e.videosDia2?.analizados ?? 0,
  videos_dia2_cargados: e.videosDia2?.cargados ?? 0,
  videos_dia2_total: e.videosDia2?.total ?? 0,
  retencion_dias: e.diasRetencion ?? null,
  videos_borrados: !!e.videosBorrados,
  con_detalle: e.conDetalle ?? true, // con el backend, todos tienen detalle
})

const mock = {
  // Sin backend, una sola página con todo (el servidor pagina con ?pagina=).
  listar() {
    return reply({ pagina: 1, total: db.experiments.length, items: db.experiments.map((e) => ({ ...e, avisoVencimiento: avisoDe(e) })) })
  },
  crear({ nombre, fecha, notas }) {
    if (db.experiments.some((e) => e.nombre.trim().toLowerCase() === nombre.trim().toLowerCase())) {
      return fail(409, 'Ya existe un experimento con ese nombre.')
    }
    const anio = fecha.slice(0, 4)
    const n = db.experiments.filter((e) => e.idExperimento.startsWith('EXP-' + anio)).length + 1
    const idExperimento = 'EXP-' + anio + '-' + String(n).padStart(2, '0')
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

// GET /experiments?pagina= → { pagina, total, items } (§6.1). Los filtros y las páginas de la
// pantalla siguen en el navegador: aquí se piden todas las páginas y se juntan.
export async function listExperiments() {
  const items = []
  for (let pagina = 1; ; pagina++) {
    const d = USE_MOCKS ? await mock.listar() : (await api.get('/experiments', { params: { pagina } })).data
    items.push(...d.items)
    if (!d.items.length || items.length >= d.total) break
  }
  return items.map(deFila)
}

// POST /experiments { nombre, fecha, notas } → { idExperimento }. Paso 1, «Datos generales»
// (nombre único, fecha y notas opcionales, §7). El servidor pone como creador a la cuenta de
// la sesión. 409 si ya existe un experimento con ese nombre. La respuesta no está en la guía:
// por confirmar con Erick.
export async function createExperiment({ titulo, fecha_inicio, notas }) {
  const datos = { nombre: titulo, fecha: fecha_inicio, notas }
  const d = USE_MOCKS ? await mock.crear(datos) : (await api.post('/experiments', datos)).data
  return { clave: d.idExperimento }
}

// Detalle de los datos de ejemplo con la forma de §6.1: grupos → tandas → videos. data.js
// guarda lo que guardaría la base; aquí se arma la respuesta. De más sobre la guía: creador y
// subidoPor (PARA_VANESA_API.md §4) y la fecha de cada video (propuesta, para la tanda).
function detalleDe(clave) {
  const exp = db.experimentDetail[clave]
  const fila = db.experiments.find((e) => e.idExperimento === clave)
  const video = (g, t, n, extra) => ({ idVideo: g.id + '/' + t.letra + '/' + n, sesion: 'Dia ' + n, duracion: 300, subidoPor: t.subido_por ?? null, ...extra })
  return {
    idExperimento: clave, nombre: exp.titulo, fecha: exp.fecha_inicio, notas: exp.notas ?? '',
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
    clave: d.idExperimento,
    titulo: d.nombre,
    fecha_inicio: d.fecha,
    notas: d.notas ?? '',
    estado: ESTADO.aFront[d.estado],
    responsable: responsableDe(d.creador),
    creador_id: d.creador?.idInstitucional ?? null,
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
          desde,
          n_cilindros: t.nCilindros,
          fecha_dia1: v1?.fecha ?? v2?.fecha ?? d.fecha,
          fecha_dia2: v2?.fecha ?? v1?.fecha ?? d.fecha,
          estado,
          dia1: v1 ? { cargado: true } : null,
          progreso: estado === 'RUNNING' && etapa ? { etapa, pct: a.porcentaje } : null,
          error: estado === 'FAILED' ? { etapa } : null,
          id_analisis: v2?.idAnalisis ?? null,
          subido_por: v2?.subidoPor ?? v1?.subidoPor ?? null,
          analisis_fecha: estado === 'DONE' ? a?.fechaAnalisis ?? null : null,
          ...(v2?.videoDisponible === false && { video_disponible: false }),
        }
        desde += t.nCilindros
        return x
      })
      return {
        id: g.idGrupo, nombre: g.etiqueta, tipo: TIPO_GRUPO.aFront[g.tipo], tratamiento: g.tratamiento,
        n_especimenes: g.tandas.reduce((n, t) => n + t.nCilindros, 0), tandas,
      }
    }),
  }
}

// GET /experiments/:clave (§6.1) y GET /experiments/:clave/status → datos generales + grupos →
// tandas, con el estado de cada análisis (pantallas 2c y 2d).
export async function getExperiment(clave) {
  const [d, s] = await Promise.all([
    USE_MOCKS
      ? (db.experimentDetail[clave] ? reply(detalleDe(clave)) : fail(404, 'No existe ese experimento.'))
      : api.get(`/experiments/${clave}`).then((r) => r.data),
    getStatus(clave),
  ])
  return deDetalle(d, s.analisis)
}

// DELETE /experiments/:clave → borra el experimento con todo lo suyo. Según D-63 no pide
// nombre ni contraseña: la pantalla solo muestra una confirmación. Solo lo borra quien lo
// creó o un Administrador (403); se rechaza con 409 si algún análisis está procesando o en
// pausa, y los que están en cola salen de la cola (R-08, D-87).
export async function deleteExperiment(clave) {
  if (USE_MOCKS) {
    const i = db.experiments.findIndex((e) => e.idExperimento === clave)
    if (i < 0) return fail(404, 'No existe ese experimento.')
    const u = usuarioDeLaSesion()
    if (!u || (!esAdmin(u) && db.experiments[i].creador?.idInstitucional !== u.idInstitucional)) {
      return fail(403, 'Solo quien creó el experimento o un Administrador puede eliminarlo.')
    }
    const tandas = db.experimentDetail[clave]?.grupos.flatMap((g) => g.tandas) || []
    if (tandas.some((t) => t.estado === 'RUNNING' || t.estado === 'PAUSED')) {
      return fail(409, 'No se puede eliminar mientras un análisis está procesando o en pausa. Inténtalo cuando termine.')
    }
    quitar(db.queue, (j) => j.clave === clave)
    db.queue.forEach((j, n) => { j.posicion = n + 1 })
    quitar(db.failedJobs, (j) => j.clave === clave)
    db.experiments.splice(i, 1)
    delete db.experimentDetail[clave]
    return reply({ ok: true })
  }
  return (await api.delete(`/experiments/${clave}`)).data
}

// Quita de la lista, sin reemplazarla, los elementos que cumplen la condición.
function quitar(lista, cond) {
  for (let k = lista.length - 1; k >= 0; k--) if (cond(lista[k])) lista.splice(k, 1)
}
