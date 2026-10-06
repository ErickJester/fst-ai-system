import api from './api'
import { USE_MOCKS, TOKEN_KEY } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { simular } from './mocks/simulador'

// GET /experiments → lista con estado agregado (pantalla 2a)
export async function listExperiments() {
  if (USE_MOCKS) {
    simular()
    return reply(db.experiments)
  }
  return (await api.get('/experiments')).data
}

// POST /experiments → paso 1, «Datos generales»: { titulo, fecha_inicio, especie, notas }.
// El servidor asigna la clave y pone como responsable a la cuenta de la sesión.
export async function createExperiment(data) {
  if (USE_MOCKS) {
    const anio = data.fecha_inicio.slice(0, 4)
    const n = db.experiments.filter((e) => e.clave.startsWith('EXP-' + anio)).length + 1
    const clave = 'EXP-' + anio + '-' + String(n).padStart(2, '0')
    const u = usuarioDeLaSesion()
    const responsable = u ? u.nombre[0] + '. ' + u.apellidos.split(' ')[0] : ''
    db.experiments.unshift({
      clave, titulo: data.titulo, tratamientos: '', fecha_inicio: data.fecha_inicio, n_grupos: 0, n_especimenes: 0,
      videos_dia2_listos: 0, videos_dia2_cargados: 0, videos_dia2_total: 0, estado: 'CARGA_INCOMPLETA',
      responsable, retencion_dias: null, videos_borrados: false, con_detalle: true,
    })
    db.experimentDetail[clave] = { clave, titulo: data.titulo, fecha_inicio: data.fecha_inicio, responsable, especie: data.especie, notas: data.notas, grupos: [] }
    return reply({ clave })
  }
  return (await api.post('/experiments', data)).data
}

// Sin backend, la cuenta sale del token simulado «mock-token-<id>».
function usuarioDeLaSesion() {
  try {
    const id = Number((sessionStorage.getItem(TOKEN_KEY) || '').replace('mock-token-', ''))
    return db.users.find((u) => u.id === id) || null
  } catch {
    return null
  }
}

// GET /experiments/:clave → datos generales + grupos → tandas (pantalla 2c)
export async function getExperiment(clave) {
  if (USE_MOCKS) {
    simular()
    const exp = db.experimentDetail[clave]
    return exp ? reply(exp) : fail(404, 'No existe ese experimento.')
  }
  return (await api.get(`/experiments/${clave}`)).data
}

// DELETE /experiments/:clave → exige el nombre exacto y la contraseña de la cuenta.
// Sin backend no hay contraseñas que comprobar: basta con que no esté vacía.
export async function deleteExperiment(clave, { titulo, password }) {
  if (USE_MOCKS) {
    const i = db.experiments.findIndex((e) => e.clave === clave)
    if (i < 0) return fail(404, 'No existe ese experimento.')
    if (db.experiments[i].titulo !== titulo) return fail(400, 'El nombre no coincide.')
    if (!password) return fail(400, 'Falta la contraseña.')
    db.experiments.splice(i, 1)
    delete db.experimentDetail[clave]
    return reply({ ok: true })
  }
  return (await api.delete(`/experiments/${clave}`, { data: { titulo, password } })).data
}
