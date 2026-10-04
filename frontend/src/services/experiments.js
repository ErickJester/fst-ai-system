import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// GET /experiments → lista con estado agregado (pantalla 2a)
export async function listExperiments() {
  if (USE_MOCKS) return reply(db.experiments)
  return (await api.get('/experiments')).data
}

// GET /experiments/:clave → datos generales + grupos → tandas (pantalla 2c)
export async function getExperiment(clave) {
  if (USE_MOCKS) {
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
