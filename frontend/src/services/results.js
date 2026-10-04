import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// GET /batches/:id/results?day= → por espécimen (s), estadísticos, línea de tiempo, nivel y calidad (2f)
export async function getBatchResults(batchId, day = 'DAY2') {
  if (USE_MOCKS) {
    const r = db.batchResults[batchId]
    if (!r) return fail(404, 'Esta tanda todavía no tiene resultados.')
    return reply(r)
  }
  return (await api.get(`/batches/${batchId}/results`, { params: { day } })).data
}

// GET /experiments/:id/group-comparison → inmovilidad media ± DE por grupo, Día 2
export async function getGroupComparison(experimentId) {
  if (USE_MOCKS) return reply(db.groupComparison[experimentId] || [])
  return (await api.get(`/experiments/${experimentId}/group-comparison`)).data
}

// URL de exportación de una tanda: format = 'csv' | 'xlsx' | 'pdf'
export function exportUrl(batchId, format) {
  return `${api.defaults.baseURL}/batches/${batchId}/reports/${format}`
}
