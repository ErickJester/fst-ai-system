import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { usuarioDeLaSesion, esAdmin } from './mocks/sesion'

// Fecha local de dentro de n días, como 'AAAA-MM-DD'.
function enDias(n) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

// Aviso de vencimiento (R-05): si los videos se borran en 7 días o menos, la fecha exacta.
const conAviso = (e) => ({
  ...e,
  aviso_vencimiento: !e.videos_borrados && e.retencion_dias != null && e.retencion_dias <= 7 ? { fecha_borrado: enDias(e.retencion_dias) } : null,
})

// GET /experiments → lista con estado agregado y aviso de vencimiento (pantalla 2a)
export async function listExperiments() {
  if (USE_MOCKS) {
    return reply(db.experiments.map(conAviso))
  }
  return (await api.get('/experiments')).data
}

// POST /experiments → paso 1, «Datos generales»: { titulo, fecha_inicio, notas } (nombre único,
// fecha y notas opcionales, §7). El servidor asigna la clave y pone como responsable a la
// cuenta de la sesión. 409 si ya existe un experimento con ese nombre.
export async function createExperiment(data) {
  if (USE_MOCKS) {
    if (db.experiments.some((e) => e.titulo.trim().toLowerCase() === data.titulo.trim().toLowerCase())) {
      return fail(409, 'Ya existe un experimento con ese nombre.')
    }
    const anio = data.fecha_inicio.slice(0, 4)
    const n = db.experiments.filter((e) => e.clave.startsWith('EXP-' + anio)).length + 1
    const clave = 'EXP-' + anio + '-' + String(n).padStart(2, '0')
    const u = usuarioDeLaSesion()
    const responsable = u ? u.nombre[0] + '. ' + u.apellidos.split(' ')[0] : ''
    const creador_id = u?.idInstitucional ?? null
    db.experiments.unshift({
      clave, titulo: data.titulo, tratamientos: [], fecha_inicio: data.fecha_inicio, n_grupos: 0, n_especimenes: 0,
      videos_dia2_listos: 0, videos_dia2_cargados: 0, videos_dia2_total: 0, estado: 'SIN_VIDEOS',
      responsable, creador_id, retencion_dias: null, videos_borrados: false, con_detalle: true,
    })
    db.experimentDetail[clave] = { clave, titulo: data.titulo, fecha_inicio: data.fecha_inicio, responsable, creador_id, notas: data.notas, grupos: [] }
    return reply({ clave })
  }
  return (await api.post('/experiments', data)).data
}

// GET /experiments/:clave → datos generales + grupos → tandas (pantalla 2c)
export async function getExperiment(clave) {
  if (USE_MOCKS) {
    const exp = db.experimentDetail[clave]
    return exp ? reply(exp) : fail(404, 'No existe ese experimento.')
  }
  return (await api.get(`/experiments/${clave}`)).data
}

// DELETE /experiments/:clave → borra el experimento con todo lo suyo. Según D-63 no pide
// nombre ni contraseña: la pantalla solo muestra una confirmación. Solo lo borra quien lo
// creó o un Administrador (403); se rechaza con 409 si algún análisis está procesando o en
// pausa, y los que están en cola salen de la cola (R-08, D-87).
export async function deleteExperiment(clave) {
  if (USE_MOCKS) {
    const i = db.experiments.findIndex((e) => e.clave === clave)
    if (i < 0) return fail(404, 'No existe ese experimento.')
    const u = usuarioDeLaSesion()
    if (!u || (!esAdmin(u) && db.experiments[i].creador_id !== u.idInstitucional)) {
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
