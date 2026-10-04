import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

const norm = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

// GET /groups/:id → tandas, ratas por cilindro y videos Día 1 / Día 2 (pantalla 2d)
export async function getGroup(id) {
  if (USE_MOCKS) {
    const g = db.groups.find((x) => x.id === Number(id))
    if (!g) return fail(404, 'No existe ese grupo.')
    return reply({ ...g, experimento: db.experimentDetail[g.experimento_id] })
  }
  return (await api.get(`/groups/${id}`)).data
}

// GET /experiments/:id/groups?q= → solo grupos de ese experimento (autocompletado de 2b)
export async function searchGroups(experimentId, q) {
  if (USE_MOCKS) {
    const nq = norm(q.trim())
    return reply(db.groups.filter((g) => g.experimento_id === Number(experimentId) &&
      (!nq || norm(`${g.nombre} ${g.tratamiento}`).includes(nq))))
  }
  return (await api.get(`/experiments/${experimentId}/groups`, { params: { q } })).data
}

// POST /experiments/:id/groups → { nombre, tipo, tratamiento }
export async function createGroup(experimentId, data) {
  if (USE_MOCKS) {
    const g = { id: Date.now(), experimento_id: Number(experimentId), n_especimenes: 0, tandas: [], ...data }
    db.groups.push(g)
    return reply(g)
  }
  return (await api.post(`/experiments/${experimentId}/groups`, data)).data
}

// POST /batches/:id/videos (multipart: file, day, n_cilindros) → { video_id, job_id, posicion_cola }
export async function uploadBatchVideo(batchId, { file, day, nCilindros }, onProgress) {
  if (USE_MOCKS) {
    for (let p = 25; p <= 100; p += 25) {
      await reply(null, 80)
      onProgress?.(p)
    }
    return reply({ video_id: Date.now(), job_id: Date.now(), posicion_cola: db.queue.length + 1 })
  }
  const fd = new FormData()
  fd.append('file', file)
  fd.append('day', day)
  fd.append('n_cilindros', String(nCilindros))
  const res = await api.post(`/batches/${batchId}/videos`, fd, {
    onUploadProgress: (e) => e.total && onProgress?.(Math.round((e.loaded / e.total) * 100)),
  })
  return res.data
}
