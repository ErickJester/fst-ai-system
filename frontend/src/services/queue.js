import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { simular, ajustarPausa, cancelarPausa, PLAZO_PAUSA_MS } from './mocks/simulador'
import { imagenDePausa } from './mocks/imagenPausa'
import { usuarioDeLaSesion } from './mocks/sesion'

// Sin backend, el PDF de diagnóstico tarda esto en estar listo después del error.
const PDF_TARDA_MS = 6000

// GET /queue → cola global en orden de llegada, análisis en pausa, con error y cancelados
// recientes (pantalla 2e)
export async function getQueue() {
  if (USE_MOCKS) {
    simular()
    return reply({ cola: db.queue, pausas: db.pausedJobs.map(conRestante), errores: db.failedJobs, cancelados: db.canceledJobs })
  }
  return (await api.get('/queue')).data
}

// GET /experiments/:clave/analyses/:id/diagnostic → PDF de diagnóstico de un análisis con
// error. Lo genera el worker al fallar; mientras no existe, el backend responde 404. El
// frontend solo lo descarga. Sin backend se simula la espera (404) y después responde null:
// no hay archivo, y la pantalla explica qué hará el botón.
export async function getDiagnostico(job) {
  if (USE_MOCKS) {
    if (job.fallo_ms && Date.now() - job.fallo_ms < PDF_TARDA_MS) return fail(404, 'El reporte de diagnóstico todavía no está listo.')
    return reply(null)
  }
  return (await api.get(`/experiments/${job.clave}/analyses/${job.job_id}/diagnostic`, { responseType: 'blob' })).data
}

// ── Pausa (R-15, D-89) ──────────────────────────────────────────────────────
// Segundos que quedan para responder (hora de la pausa + 5 minutos).
const restante = (job, ahora = Date.now()) => Math.max(0, Math.ceil((job.pausa.hora_ms + PLAZO_PAUSA_MS - ahora) / 1000))
const conRestante = (job) => ({ ...job, segundos_restantes: restante(job) })

// Análisis en pausa con permiso para atenderlo: quien subió el video o un Administrador.
function pausaPropia(clave, id) {
  simular()
  const job = db.pausedJobs.find((j) => j.job_id === Number(id) && j.clave === clave)
  if (!job) return { error: fail(409, 'El análisis ya no está en pausa.') }
  const u = usuarioDeLaSesion()
  if (!u || (u.role !== 'ADMIN' && job.subido_por !== u.id)) {
    return { error: fail(403, 'Solo quien subió el video o un Administrador puede atender esta pausa.') }
  }
  return { job }
}

// GET /experiments/:clave/analyses/:id/pause → { cilindro, desdeSegundo, segundosRestantes,
// ancho, alto, recuadros: [{ cilindro, x0, y0, x1, y1 }] } (coordenadas de la imagen fija)
export async function getPausa(clave, id) {
  if (USE_MOCKS) {
    const { job, error } = pausaPropia(clave, id)
    if (error) return error
    const p = job.pausa
    return reply({ cilindro: p.cilindro, desdeSegundo: p.desde_segundo, segundosRestantes: restante(job), ancho: p.ancho, alto: p.alto, recuadros: p.recuadros })
  }
  return (await api.get(`/experiments/${clave}/analyses/${id}/pause`)).data
}

// GET /experiments/:clave/analyses/:id/pause/image → imagen fija del instante de la pausa
export async function getImagenPausa(clave, id) {
  if (USE_MOCKS) {
    const { job, error } = pausaPropia(clave, id)
    if (error) return error
    return reply(imagenDePausa(job.pausa, job.n_especimenes))
  }
  return (await api.get(`/experiments/${clave}/analyses/${id}/pause/image`, { responseType: 'blob' })).data
}

// POST /experiments/:clave/analyses/:id/pause/adjust { cilindro, x0, y0, x1, y1 } → el
// análisis vuelve a la cola y sigue desde el segundo de la pausa; 409 si ya no está en pausa.
export async function ajustarRecuadro(clave, id, recuadro) {
  if (USE_MOCKS) {
    const { job, error } = pausaPropia(clave, id)
    if (error) return error
    ajustarPausa(job.job_id, recuadro)
    return reply({ ok: true })
  }
  return (await api.post(`/experiments/${clave}/analyses/${id}/pause/adjust`, recuadro)).data
}

// POST /experiments/:clave/analyses/:id/cancel → el análisis queda cancelado; 409 si ya no
// está en pausa (por ejemplo, porque pasaron los 5 minutos).
export async function cancelarAnalisis(clave, id) {
  if (USE_MOCKS) {
    const { job, error } = pausaPropia(clave, id)
    if (error) return error
    cancelarPausa(job.job_id, 'usuario')
    return reply({ ok: true })
  }
  return (await api.post(`/experiments/${clave}/analyses/${id}/cancel`)).data
}
