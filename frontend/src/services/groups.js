import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// GET /experiments/:clave/groups/:gid → grupo con sus tandas y videos (pantalla 2d)
export async function getGroup(clave, gid) {
  if (USE_MOCKS) {
    const exp = db.experimentDetail[clave]
    const grupo = exp?.grupos.find((g) => g.id === gid)
    if (!grupo) return fail(404, 'No existe ese grupo.')
    return reply({ experimento: { clave: exp.clave, titulo: exp.titulo }, ...grupo })
  }
  return (await api.get(`/experiments/${clave}/groups/${gid}`)).data
}

// POST /experiments/:clave/groups → { nombre, tipo, tratamiento }; el grupo nuevo empieza sin tandas
export async function createGroup(clave, data) {
  if (USE_MOCKS) {
    const exp = db.experimentDetail[clave]
    if (!exp) return fail(404, 'No existe ese experimento.')
    const g = { id: 'G-' + String(exp.grupos.length + 1).padStart(2, '0'), n_especimenes: 0, tandas: [], ...data }
    exp.grupos.push(g)
    resumir(clave)
    return reply(g)
  }
  return (await api.post(`/experiments/${clave}/groups`, data)).data
}

// POST /experiments/:clave/groups/:gid/batches/:letra/videos
// (multipart: file, dia, n_cilindros) → { job_id, posicion_cola }
export async function uploadBatchVideo(clave, gid, letra, { file, dia, nCilindros }, onProgress) {
  if (USE_MOCKS) {
    const g = db.experimentDetail[clave]?.grupos.find((x) => x.id === gid)
    if (!g) return fail(404, 'No existe ese grupo.')
    for (let p = 25; p <= 100; p += 25) {
      await reply(null, 80)
      onProgress?.(p)
    }
    // La tanda queda registrada y su análisis entra al final de la cola.
    let t = g.tandas.find((x) => x.letra === letra)
    if (!t) {
      const desde = g.tandas.reduce((a, x) => a + x.n_cilindros, 0) + 1
      const hoy = new Date().toISOString().slice(0, 10)
      t = { letra, desde, n_cilindros: nCilindros, fecha_dia1: hoy, fecha_dia2: hoy, estado: 'SIN_VIDEO', dia1: null, progreso: null, error: null, analisis_fecha: null }
      g.tandas.push(t)
      g.n_especimenes += nCilindros
    }
    if (dia === 'DAY1') t.dia1 = { estado: 'QUEUED' }
    else Object.assign(t, { estado: 'QUEUED', progreso: null, error: null, analisis_fecha: null })
    const job = {
      job_id: Date.now(), posicion: db.queue.length + 1, experimento: db.experimentDetail[clave].titulo.split(' · ')[0],
      grupo: g.nombre, tanda: letra, dia, n_especimenes: nCilindros, status: 'QUEUED', stage: null, progress_pct: 0, confianza: null, error: null,
    }
    db.queue.push(job)
    resumir(clave)
    return reply({ job_id: job.job_id, posicion_cola: job.posicion })
  }
  const fd = new FormData()
  fd.append('file', file)
  fd.append('dia', dia)
  fd.append('n_cilindros', String(nCilindros))
  const res = await api.post(`/experiments/${clave}/groups/${gid}/batches/${letra}/videos`, fd, {
    onUploadProgress: (e) => e.total && onProgress?.(Math.round((e.loaded / e.total) * 100)),
  })
  return res.data
}

// Sin backend, la fila del experimento en la lista (2a) se recalcula a partir de sus
// grupos y tandas, como lo haría el servidor.
function resumir(clave) {
  const exp = db.experimentDetail[clave]
  const fila = db.experiments.find((e) => e.clave === clave)
  if (!exp || !fila) return
  const tandas = exp.grupos.flatMap((g) => g.tandas)
  const total = tandas.length
  const cargados = tandas.filter((t) => t.estado !== 'SIN_VIDEO').length
  const listos = tandas.filter((t) => t.estado === 'DONE').length
  Object.assign(fila, {
    n_grupos: exp.grupos.length,
    n_especimenes: exp.grupos.reduce((a, g) => a + g.n_especimenes, 0),
    tratamientos: [...new Set(exp.grupos.map((g) => g.tratamiento))].join(' '),
    videos_dia2_total: total,
    videos_dia2_cargados: cargados,
    videos_dia2_listos: listos,
    estado: !total || cargados < total ? 'CARGA_INCOMPLETA' : listos === total ? 'CONCLUIDO' : 'EN_ANALISIS',
    retencion_dias: fila.retencion_dias ?? (total ? 30 : null),
  })
}
