import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// GET /experiments/:clave/groups/:gid/batches/:letra/results?day= → por espécimen (s),
// línea de tiempo, nivel y calidad del análisis (2f)
export async function getBatchResults(clave, gid, letra, dia = 'DAY2') {
  if (USE_MOCKS) {
    const r = db.batchResults[[clave, gid, letra, dia].join('/')]
    return r ? reply(r) : fail(404, 'Esta tanda todavía no tiene resultados.')
  }
  return (await api.get(`/experiments/${clave}/groups/${gid}/batches/${letra}/results`, { params: { day: dia } })).data
}

// GET /experiments/:clave/group-comparison → inmovilidad media ± DE por grupo, Día 2
export async function getGroupComparison(clave) {
  if (USE_MOCKS) return reply(db.groupComparison[clave] || [])
  return (await api.get(`/experiments/${clave}/group-comparison`)).data
}
