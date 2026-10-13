// Simulación de la cola de análisis con datos de ejemplo. Sin backend no hay worker:
// aquí los trabajos avanzan solos según el tiempo transcurrido, uno a la vez, como lo
// haría el pipeline. Al terminar cada trabajo se actualiza su tanda, llega una
// notificación y empieza el siguiente. No usa temporizadores: cada lectura de los
// servicios calcula hasta dónde debió avanzar desde la vez anterior.
import * as db from './data'
import { resumir } from './resumen'
import { DIA, PCT_ETAPA, enlaceResultados, enlacePausa, mensajeErrorAnalisis } from '../../lib/fst'

const ETAPAS = ['PREPROCESSING', 'ROI_DETECTION', 'CLASSIFICATION']
export const POR_ETAPA_MS = 8000
export const DURACION_MS = POR_ETAPA_MS * ETAPAS.length
// Uno de cada tres trabajos falla en la etapa de cilindros (no se hallan los tubos), al
// terminar esa etapa. Solo hay trabajos de Día 2: el Día 1 no se analiza (D-54).
const CADA_CUANTOS_FALLA = 3
// Un trabajo con simular_pausa (o un video con «pausa» en el nombre) se pausa a la mitad
// de la clasificación: el pipeline dejó de ver a un espécimen 3 s seguidos (R-15).
const PAUSA_EN_MS = 2.5 * POR_ETAPA_MS
// Sin respuesta en 5 minutos, el análisis en pausa se cancela (PAUSE_TIMEOUT_MINUTES).
export const PLAZO_PAUSA_MS = 5 * 60 * 1000

// Lo llaman los servicios al leer. En las pruebas automáticas no corre: ahí se prueba
// avanzarSimulacion() con tiempos fijos.
export function simular() {
  if (import.meta.env.MODE !== 'test') avanzarSimulacion()
}

export function avanzarSimulacion(ahora = Date.now()) {
  const sim = db.simulacion
  if (sim.ultimo == null) sim.ultimo = ahora
  let cursor = sim.ultimo // momento en que quedó libre el pipeline

  while (db.queue.length) {
    const job = db.queue[0]
    if (job.status === 'QUEUED') {
      job.inicio_ms = Math.max(cursor, job.creado_ms || 0)
      if (job.inicio_ms > ahora) break
      Object.assign(job, { status: 'RUNNING', stage: ETAPAS[0], progress_pct: 0 })
      // Uno que vuelve de una pausa sigue desde el segundo de la pausa, en la clasificación.
      if (job.reanudar) job.inicio_ms -= PAUSA_EN_MS
    }
    // Un trabajo que ya venía procesando (datos iniciales) va a la mitad de su etapa.
    if (job.inicio_ms == null) job.inicio_ms = ahora - (ETAPAS.indexOf(job.stage) + 0.5) * POR_ETAPA_MS
    // Cómo termina se decide una vez, al empezar.
    if (job.desenlace == null) {
      const falla = sim.terminados % CADA_CUANTOS_FALLA === CADA_CUANTOS_FALLA - 1
      job.desenlace = job.reanudar ? 'ok' : falla ? 'error' : job.simular_pausa ? 'pausa' : 'ok'
    }

    const fin = job.inicio_ms + { error: 2 * POR_ETAPA_MS, pausa: PAUSA_EN_MS, ok: DURACION_MS }[job.desenlace]
    if (ahora < fin) {
      progreso(job, ahora)
      break
    }
    terminar(job, fin)
    cursor = fin
  }
  // Las pausas sin respuesta en 5 minutos se cancelan.
  for (const job of [...db.pausedJobs]) {
    const vence = job.pausa.hora_ms + PLAZO_PAUSA_MS
    if (ahora >= vence) cancelarPausa(job.job_id, 'plazo', vence)
  }
  sim.ultimo = ahora
}

function buscarTanda(job) {
  const g = db.experimentDetail[job.clave]?.grupos.find((x) => x.id === job.gid)
  return g?.tandas.find((t) => t.letra === job.tanda)
}

const renumerar = () => db.queue.forEach((j, i) => { j.posicion = i + 1 })
const nombreDe = (job) => job.experimento + ' · ' + job.grupo + ' · Tanda ' + job.tanda + ' · ' + DIA[job.dia]

function progreso(job, ahora) {
  const tope = job.desenlace === 'error' ? 1 : ETAPAS.length - 1
  const etapa = ETAPAS[Math.min(tope, Math.floor((ahora - job.inicio_ms) / POR_ETAPA_MS))]
  const pct = PCT_ETAPA[etapa]
  Object.assign(job, { stage: etapa, progress_pct: pct })

  const t = buscarTanda(job)
  if (!t) return
  Object.assign(t, { estado: 'RUNNING', progreso: { etapa, pct } })
}

function terminar(job, fin) {
  db.queue.shift()
  renumerar()
  db.simulacion.terminados++

  const t = buscarTanda(job)
  const nombre = nombreDe(job)

  if (job.desenlace === 'error') {
    const etapa = 'ROI_DETECTION'
    // fallo_ms: el PDF de diagnóstico tarda unos segundos en estar listo (services/queue.js).
    db.failedJobs.unshift({ ...job, status: 'FAILED', stage: etapa, progress_pct: PCT_ETAPA[etapa], fallo_ms: fin, posicion: undefined })
    if (t) Object.assign(t, { estado: 'FAILED', progreso: null, error: { etapa } })
    notificar('ANALYSIS_FAILED', 'Error en el análisis', nombre + '. ' + mensajeErrorAnalisis(etapa), '/analisis', fin)
  } else if (job.desenlace === 'pausa') {
    // Primero el archivo de pausa (aquí, job.pausa) y después el estado (D-89).
    const pausa = datosDePausa(job, fin)
    db.pausedJobs.unshift({ ...job, status: 'PAUSED', stage: 'CLASSIFICATION', progress_pct: PCT_ETAPA.CLASSIFICATION, pausa, posicion: undefined })
    if (t) Object.assign(t, { estado: 'PAUSED', progreso: null })
    notificar('ANALYSIS_PAUSED', 'Análisis en pausa', nombre + '. No se ve al espécimen del cilindro ' + pausa.cilindro + ' (segundo ' + pausa.desde_segundo + '). Ajusta el recuadro en 5 minutos o se cancela.', enlacePausa(job.clave, job.job_id), fin)
  } else {
    if (t) Object.assign(t, { estado: 'DONE', progreso: null, error: null, analisis_fecha: new Date(fin).toISOString().slice(0, 10) })
    notificar('ANALYSIS_DONE', 'Análisis completado', nombre + '.', enlaceResultados(job.clave, job.gid, job.tanda), fin)
  }
  if (job.clave) resumir(job.clave)
}

// Imagen fija de 1280 × 720 con un recuadro por cilindro; el del cilindro 2 quedó corrido,
// por eso el pipeline dejó de ver a su espécimen.
export const ANCHO_PAUSA = 1280
export const ALTO_PAUSA = 720
function datosDePausa(job, hora_ms) {
  const n = job.n_especimenes
  const paso = ANCHO_PAUSA / n
  const recuadros = Array.from({ length: n }, (_, i) => {
    const x0 = Math.round(paso * i + paso / 2 - 110)
    return { cilindro: i + 1, x0, y0: 130, x1: x0 + 220, y1: 640 }
  })
  const cilindro = Math.min(2, n)
  const r = recuadros[cilindro - 1]
  Object.assign(r, { x0: r.x0 + 80, x1: r.x1 + 80, y0: r.y0 + 70, y1: r.y1 + 70 })
  return { cilindro, desde_segundo: 142, hora_ms, ancho: ANCHO_PAUSA, alto: ALTO_PAUSA, recuadros, ajuste: null }
}

// Ajustar y continuar: guarda el recuadro nuevo y el análisis vuelve a la cola, por delante
// de los que llegaron después (conserva su orden de llegada). false si ya no está en pausa.
export function ajustarPausa(jobId, recuadro, ahora = Date.now()) {
  const i = db.pausedJobs.findIndex((j) => j.job_id === jobId)
  if (i < 0) return false
  const [job] = db.pausedJobs.splice(i, 1)
  job.pausa.ajuste = { ...recuadro, hora: new Date(ahora).toISOString() }
  Object.assign(job, { status: 'QUEUED', stage: null, progress_pct: 0, reanudar: true, desenlace: null, inicio_ms: null, creado_ms: ahora })
  let k = db.queue.findIndex((j) => j.status === 'QUEUED' && j.job_id > job.job_id)
  if (k < 0) k = db.queue.length
  db.queue.splice(k, 0, job)
  renumerar()
  const t = buscarTanda(job)
  if (t) Object.assign(t, { estado: 'QUEUED', progreso: null })
  if (job.clave) resumir(job.clave)
  return true
}

// Cancelar: por el usuario o porque pasaron 5 minutos (solo esta avisa). false si ya no
// está en pausa: el segundo que llegue no hace nada.
export function cancelarPausa(jobId, motivo, ahora = Date.now()) {
  const i = db.pausedJobs.findIndex((j) => j.job_id === jobId)
  if (i < 0) return false
  const [job] = db.pausedJobs.splice(i, 1)
  db.canceledJobs.unshift({ ...job, status: 'CANCELED', motivo, cancelado_ms: ahora })
  const t = buscarTanda(job)
  if (t) Object.assign(t, { estado: 'CANCELED', progreso: null })
  if (motivo === 'plazo') {
    notificar('ANALYSIS_CANCELED', 'Análisis cancelado', nombreDe(job) + '. Pasaron 5 minutos sin respuesta a la pausa.', '/analisis', ahora)
  }
  if (job.clave) resumir(job.clave)
  return true
}

function notificar(tipo, titulo, texto, enlace, fin) {
  const id = db.notifications.reduce((m, n) => Math.max(m, n.id), 0) + 1
  db.notifications.unshift({ id, tipo, titulo, texto, enlace, creada: new Date(fin).toISOString(), is_read: false })
}
