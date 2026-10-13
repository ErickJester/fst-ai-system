import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { simular } from './mocks/simulador'
import { pdfDeTexto } from './mocks/pdf'
import { DIA, ETAPA, mensajeErrorAnalisis } from '../lib/fst'

// Sin backend, el PDF de diagnóstico tarda esto en estar listo después del error.
const PDF_TARDA_MS = 6000

// GET /queue → cola global en orden de llegada y trabajos con error recientes (pantalla 2e)
export async function getQueue() {
  if (USE_MOCKS) {
    simular()
    return reply({ cola: db.queue, errores: db.failedJobs })
  }
  return (await api.get('/queue')).data
}

// GET /experiments/:clave/analyses/:id/diagnostic → PDF de diagnóstico de un análisis con
// error. Lo escribe el worker al fallar; mientras no existe, el backend responde 404.
export async function getDiagnostico(job) {
  if (USE_MOCKS) {
    if (job.fallo_ms && Date.now() - job.fallo_ms < PDF_TARDA_MS) return fail(404, 'El reporte de diagnóstico todavía no está listo.')
    return reply(pdfDeTexto([
      'Reporte de diagnóstico',
      job.experimento + ' · ' + job.grupo + ' · Tanda ' + job.tanda + ' · ' + DIA[job.dia],
      'Detenido en: ' + ETAPA[job.stage] + ' (' + job.progress_pct + ' %)',
      mensajeErrorAnalisis(job.stage),
    ]))
  }
  return (await api.get(`/experiments/${job.clave}/analyses/${job.job_id}/diagnostic`, { responseType: 'blob' })).data
}
