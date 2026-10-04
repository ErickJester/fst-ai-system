import api from './api'
import { USE_MOCKS } from './config'
import { reply } from './mocks/delay'
import * as db from './mocks/data'

// GET /queue → cola global en orden de llegada y trabajos con error recientes (pantalla 2e)
export async function getQueue() {
  if (USE_MOCKS) return reply({ cola: db.queue, errores: db.failedJobs })
  return (await api.get('/queue')).data
}
