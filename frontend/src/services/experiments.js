import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// GET /experiments → lista con estado agregado (pantalla 2a)
export async function listExperiments() {
  if (USE_MOCKS) return reply(db.experiments)
  return (await api.get('/experiments')).data
}

// GET /experiments/:id → datos generales + grupos → tandas (pantalla 2c)
export async function getExperiment(id) {
  if (USE_MOCKS) {
    const base = db.experimentDetail[id]
    if (!base) return fail(404, 'No existe ese experimento.')
    return reply({ ...base, grupos: db.groups.filter((g) => g.experimento_id === Number(id)) })
  }
  return (await api.get(`/experiments/${id}`)).data
}

// POST /experiments → paso 1, «Datos generales»
export async function createExperiment(data) {
  if (USE_MOCKS) return reply({ id: Date.now(), ...data })
  return (await api.post('/experiments', data)).data
}

// DELETE /experiments/:id → exige el nombre exacto y la contraseña de la cuenta
export async function deleteExperiment(id, { nombre, password }) {
  if (USE_MOCKS) {
    const i = db.experiments.findIndex((e) => e.id === Number(id))
    if (i < 0) return fail(404, 'No existe ese experimento.')
    if (db.experiments[i].nombre !== nombre) return fail(400, 'El nombre no coincide.')
    if (password !== db.MOCK_PASSWORD) return fail(400, 'La contraseña no es correcta.')
    db.experiments.splice(i, 1)
    return reply({ ok: true, deleted_id: Number(id) })
  }
  return (await api.delete(`/experiments/${id}`, { data: { nombre, password } })).data
}
