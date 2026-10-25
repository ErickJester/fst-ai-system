import api from './api'
import { usaEjemplo } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { resumir } from './mocks/resumen'
import { usuarioDeLaSesion, esAdmin } from './mocks/sesion'
import { ESTADO, ETAPA } from '../lib/contrato'
import { PCT_ETAPA } from '../lib/fst'

// Datos de ejemplo, salvo que esta parte ya esté conectada (VITE_API_REAL).
const USE_MOCKS = usaEjemplo('progreso')

// El estado de los análisis, las pausas y los errores los produce el worker (equipo de
// backend). Sin backend, los datos de ejemplo quedan fijos: aquí solo se responde lo que el
// servidor respondería. «Análisis en curso» se arma con /status en services/progreso.js.

// GET /experiments/:clave/status → { analisis: [{ idAnalisis, tanda, sesion, estado, etapa,
// porcentaje, pausa, diagnosticoDisponible }] } (§6.1), con los valores de la guía. Trae todos
// los análisis vigentes del experimento, también los completados: el detalle toma de aquí el
// estado de cada tanda. De más sobre la guía (propuestos): fechaAnalisis, para «Análisis
// vigente»; pausa.desdeSegundo; y motivoCancelacion ('usuario' o 'plazo'), para explicar por
// qué se canceló (§9.4).
export async function getStatus(clave) {
  // El detalle del experimento no funciona sin /status: va con la parte «experimentos».
  if (usaEjemplo('experimentos')) {
    const exp = db.experimentDetail[clave]
    if (!exp) return fail(404, 'No existe ese experimento.')
    return reply({ analisis: exp.grupos.flatMap((g) => g.tandas.filter((t) => t.id_analisis && t.estado !== 'SIN_VIDEO').map((t) => analisisDe(g, t))) })
  }
  return (await api.get(`/experiments/${clave}/status`)).data
}

// Un análisis de los datos de ejemplo con la forma de /status.
function analisisDe(g, t) {
  const pausado = db.pausedJobs.find((j) => j.job_id === t.id_analisis)
  const fallido = db.failedJobs.find((j) => j.job_id === t.id_analisis)
  const enCola = db.queue.find((j) => j.job_id === t.id_analisis) || pausado
  const etapa = t.progreso?.etapa || t.error?.etapa || pausado?.stage || null
  const porcentaje = t.estado === 'DONE' ? 100 : t.progreso?.pct ?? pausado?.progress_pct ?? fallido?.progress_pct ?? (etapa ? PCT_ETAPA[etapa] : 0)
  return {
    idAnalisis: t.id_analisis,
    tanda: g.nombre + ' · ' + t.letra,
    sesion: 'Dia 2',
    estado: ESTADO.aGuia[t.estado],
    etapa: etapa ? ETAPA.aGuia[etapa] : null,
    porcentaje,
    pausa: pausado ? { cilindro: pausado.pausa.cilindro, desdeSegundo: pausado.pausa.desde_segundo, segundosRestantes: restante(pausado) } : null,
    diagnosticoDisponible: t.estado === 'FAILED',
    // Mientras no termina, fechaAnalisis es la hora en que entró a la cola (D-79).
    fechaAnalisis: t.estado === 'DONE' ? t.analisis_fecha : enCola?.encolado ?? (enCola?.creado_ms ? new Date(enCola.creado_ms).toISOString() : null),
    motivoCancelacion: db.canceledJobs.find((j) => j.job_id === t.id_analisis)?.motivo ?? null,
  }
}

// GET /experiments/:clave/analyses/:id/diagnostic → PDF de diagnóstico de un análisis con
// error. Lo genera el worker al fallar; mientras no existe, el backend responde 404 (la
// pantalla muestra «Preparando…» y reintenta). El frontend solo lo descarga. Sin backend no
// hay archivo: responde null y la pantalla explica qué hará el botón.
export async function getDiagnostico(job) {
  if (USE_MOCKS) return reply(null)
  return (await api.get(`/experiments/${job.clave}/analyses/${job.job_id}/diagnostic`, { responseType: 'blob' })).data
}

// GET /experiments/:clave/analyses/:id/control → la imagen de control del pipeline (JPG): los tubos que encontró, con
// su zona medida, la línea de agua y el fondo. La pantalla de un análisis con error la muestra. La etiqueta <img> no
// puede mandar el Authorization, por eso se pide como blob (igual que el PDF de diagnóstico) y la pantalla la muestra
// con una URL de objeto. Sin backend no hay imagen: responde null.
export async function getControl(job) {
  if (USE_MOCKS) return reply(null)
  return (await api.get(`/experiments/${job.clave}/analyses/${job.job_id}/control`, { responseType: 'blob' })).data
}

// ── Pausa (R-15, D-89) ──────────────────────────────────────────────────────
// Plazo para responder (PAUSE_TIMEOUT_HOURS). Al vencer, el worker cancela el análisis.
const PLAZO_PAUSA_MS = 72 * 60 * 60 * 1000

// Segundos que quedan para responder (hora de la pausa + 72 horas). Sin backend, la pausa
// de ejemplo empieza la primera vez que se consulta, para poder ver el plazo corriendo.
function restante(job, ahora = Date.now()) {
  if (job.pausa.hora_ms == null) job.pausa.hora_ms = ahora
  return Math.max(0, Math.ceil((job.pausa.hora_ms + PLAZO_PAUSA_MS - ahora) / 1000))
}

const buscarTanda = (job) => db.experimentDetail[job.clave]?.grupos.find((g) => g.id === job.gid)?.tandas.find((t) => t.letra === job.tanda)

// Análisis en pausa con permiso para atenderlo: quien subió el video o un Administrador.
function pausaPropia(clave, id) {
  const i = db.pausedJobs.findIndex((j) => j.job_id === Number(id) && String(j.clave) === String(clave))
  if (i < 0) return { error: fail(409, 'El análisis ya no está en pausa.') }
  const u = usuarioDeLaSesion()
  if (!u || (!esAdmin(u) && db.pausedJobs[i].subido_por !== u.idInstitucional)) {
    return { error: fail(403, 'Solo quien subió el video o un Administrador puede atender esta pausa.') }
  }
  return { job: db.pausedJobs[i], i }
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
// (pausa.jpg, la guarda el pipeline). Sin backend no hay imagen: responde null.
export async function getImagenPausa(clave, id) {
  if (USE_MOCKS) {
    const { error } = pausaPropia(clave, id)
    return error || reply(null)
  }
  return (await api.get(`/experiments/${clave}/analyses/${id}/pause/image`, { responseType: 'blob' })).data
}

// POST /experiments/:clave/analyses/:id/pause/adjust { cilindro, x0, y0, x1, y1 } → el
// backend guarda el recuadro y el análisis vuelve a la cola, por delante de los que llegaron
// después; el worker lo retoma desde el segundo de la pausa. 409 si ya no está en pausa.
export async function ajustarRecuadro(clave, id, recuadro) {
  if (USE_MOCKS) {
    const { job, i, error } = pausaPropia(clave, id)
    if (error) return error
    // Igual que el backend: si ningún borde se movió más de 3 px, no es un ajuste (422).
    const automatico = job.pausa.recuadros.find((r) => r.cilindro === recuadro.cilindro)
    const movido = automatico && Math.max(...['x0', 'y0', 'x1', 'y1'].map((k) => Math.abs(recuadro[k] - automatico[k])))
    if (movido !== undefined && movido <= 3) {
      return fail(422, 'El recuadro casi no cambió: ningún borde se movió más de 3 px. Muévelo sobre el tubo o elige «El recuadro está bien, continuar».')
    }
    db.pausedJobs.splice(i, 1)
    job.pausa.ajuste = { ...recuadro, hora: new Date().toISOString() }
    Object.assign(job, { status: 'QUEUED', stage: null, progress_pct: 0 })
    let k = db.queue.findIndex((j) => j.status === 'QUEUED' && j.job_id > job.job_id)
    if (k < 0) k = db.queue.length
    db.queue.splice(k, 0, job)
    db.queue.forEach((j, n) => { j.posicion = n + 1 })
    const t = buscarTanda(job)
    if (t) Object.assign(t, { estado: 'QUEUED', progreso: null })
    resumir(clave)
    return reply({ ok: true })
  }
  return (await api.post(`/experiments/${clave}/analyses/${id}/pause/adjust`, recuadro)).data
}

// POST /experiments/:clave/analyses/:id/pause/continue (sin cuerpo) → «El recuadro está bien»: el
// análisis vuelve a la cola sin cambiar el recuadro, y el worker no vuelve a pausar esa racha.
// 409 si ya no está en pausa.
export async function continuarRecuadro(clave, id) {
  if (USE_MOCKS) {
    const { job, i, error } = pausaPropia(clave, id)
    if (error) return error
    db.pausedJobs.splice(i, 1)
    job.pausa.continuar = { hora: new Date().toISOString() }
    Object.assign(job, { status: 'QUEUED', stage: null, progress_pct: 0 })
    let k = db.queue.findIndex((j) => j.status === 'QUEUED' && j.job_id > job.job_id)
    if (k < 0) k = db.queue.length
    db.queue.splice(k, 0, job)
    db.queue.forEach((j, n) => { j.posicion = n + 1 })
    const t = buscarTanda(job)
    if (t) Object.assign(t, { estado: 'QUEUED', progreso: null })
    resumir(clave)
    return reply({ ok: true })
  }
  return (await api.post(`/experiments/${clave}/analyses/${id}/pause/continue`)).data
}

// POST /experiments/:clave/analyses/:id/cancel → el análisis queda cancelado; 409 si ya no
// está en pausa (por ejemplo, porque pasaron las 72 horas).
export async function cancelarAnalisis(clave, id) {
  if (USE_MOCKS) {
    const { job, i, error } = pausaPropia(clave, id)
    if (error) return error
    db.pausedJobs.splice(i, 1)
    db.canceledJobs.unshift({ ...job, status: 'CANCELED', motivo: 'usuario' })
    const t = buscarTanda(job)
    if (t) Object.assign(t, { estado: 'CANCELED', progreso: null })
    resumir(clave)
    return reply({ ok: true })
  }
  return (await api.post(`/experiments/${clave}/analyses/${id}/cancel`)).data
}
