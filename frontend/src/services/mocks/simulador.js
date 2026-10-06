// Simulación de la cola de análisis con datos de ejemplo. Sin backend no hay worker:
// aquí los trabajos avanzan solos según el tiempo transcurrido, uno a la vez, como lo
// haría el pipeline. Al terminar cada trabajo se actualiza su tanda, llega una
// notificación y empieza el siguiente. No usa temporizadores: cada lectura de los
// servicios calcula hasta dónde debió avanzar desde la vez anterior.
import * as db from './data'
import { resumir } from './resumen'
import { DIA, enlaceResultados } from '../../lib/fst'

const ETAPAS = ['PREPROCESSING', 'ROI_DETECTION', 'TRACKING', 'CLASSIFICATION']
export const POR_ETAPA_MS = 8000
export const DURACION_MS = POR_ETAPA_MS * ETAPAS.length
// Uno de cada tres trabajos falla en la detección de cilindros, a la mitad del análisis.
const CADA_CUANTOS_FALLA = 3

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
    }
    // Un trabajo que ya venía procesando (datos iniciales) conserva su avance.
    if (job.inicio_ms == null) job.inicio_ms = ahora - (job.progress_pct / 100) * DURACION_MS

    const falla = sim.terminados % CADA_CUANTOS_FALLA === CADA_CUANTOS_FALLA - 1
    const fin = job.inicio_ms + (falla ? DURACION_MS / 2 : DURACION_MS)
    if (ahora < fin) {
      progreso(job, ahora, falla)
      break
    }
    terminar(job, falla, fin)
    cursor = fin
  }
  sim.ultimo = ahora
}

function buscarTanda(job) {
  const g = db.experimentDetail[job.clave]?.grupos.find((x) => x.id === job.gid)
  return g?.tandas.find((t) => t.letra === job.tanda)
}

function progreso(job, ahora, falla) {
  const tope = falla ? 49 : 99
  const pct = Math.min(tope, Math.floor(((ahora - job.inicio_ms) / DURACION_MS) * 100))
  const etapa = ETAPAS[Math.min(ETAPAS.length - 1, Math.floor(pct / 25))]
  Object.assign(job, { stage: etapa, progress_pct: pct })
  if (pct >= 50 && job.confianza == null) job.confianza = 0.8 + (job.job_id % 10) / 100

  const t = buscarTanda(job)
  if (!t) return
  if (job.dia === 'DAY1') t.dia1 = { estado: 'RUNNING' }
  else Object.assign(t, { estado: 'RUNNING', progreso: { etapa, pct } })
}

function terminar(job, falla, fin) {
  db.queue.shift()
  db.queue.forEach((j, i) => { j.posicion = i + 1 })
  db.simulacion.terminados++

  const t = buscarTanda(job)
  const nombre = job.experimento + ' · ' + job.grupo + ' · Tanda ' + job.tanda + ' · ' + DIA[job.dia]

  if (falla) {
    const confianza = 0.5 + (job.job_id % 10) / 100
    const error = { codigo: 'E-DET-070', mensaje: 'Confianza de detección ' + confianza.toFixed(2) + ', menor a 0.70: no se detectaron los ' + job.n_especimenes + ' cilindros.' }
    db.failedJobs.unshift({ ...job, status: 'FAILED', stage: 'ROI_DETECTION', progress_pct: 50, confianza, error, posicion: undefined })
    if (t && job.dia === 'DAY1') t.dia1 = { estado: 'FAILED' }
    else if (t) Object.assign(t, { estado: 'FAILED', progreso: null, error })
    notificar('ANALYSIS_FAILED', 'Error en el análisis', nombre + '. Confianza de detección ' + confianza.toFixed(2) + ', menor a 0.70.', '/analisis', fin)
  } else {
    if (t && job.dia === 'DAY1') t.dia1 = { estado: 'DONE' }
    else if (t) Object.assign(t, { estado: 'DONE', progreso: null, error: null, analisis_fecha: new Date(fin).toISOString().slice(0, 10) })
    const enlace = job.dia === 'DAY2' ? enlaceResultados(job.clave, job.gid, job.tanda) : `/experimentos/${job.clave}/grupos/${job.gid}`
    notificar('ANALYSIS_DONE', 'Análisis completado', nombre + '.', enlace, fin)
  }
  if (job.clave) resumir(job.clave)
}

function notificar(tipo, titulo, texto, enlace, fin) {
  const id = db.notifications.reduce((m, n) => Math.max(m, n.id), 0) + 1
  db.notifications.unshift({ id, tipo, titulo, texto, enlace, creada: new Date(fin).toISOString(), is_read: false })
}
