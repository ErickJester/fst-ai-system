import api from './api'
import { USE_MOCKS } from './config'
import { reply } from './mocks/delay'
import * as db from './mocks/data'

// GET /experiments → lista con estado agregado (pantalla 2a)
export async function listExperiments() {
  if (USE_MOCKS) return reply(db.experiments)
  return (await api.get('/experiments')).data
}
