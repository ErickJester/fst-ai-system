import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { usuarioDeLaSesion, esAdmin } from './mocks/sesion'

// Tipos (los seis del documento): ANALYSIS_DONE, ANALYSIS_FAILED, ANALYSIS_PAUSED,
// ANALYSIS_CANCELED, DISK_ALERT (solo Administradores) y VIDEO_DELETED.
// Sin backend, las que van solo a Administradores llevan solo_admin.
const mias = () => db.notifications.filter((n) => !n.solo_admin || esAdmin(usuarioDeLaSesion()))

// GET /notifications → las de la cuenta, de la más nueva a la más vieja
export async function listNotifications() {
  if (USE_MOCKS) {
    return reply(mias(), 60)
  }
  return (await api.get('/notifications')).data
}

// PATCH /notifications/:id → { is_read }
export async function setRead(id, isRead) {
  if (USE_MOCKS) {
    const n = db.notifications.find((x) => x.id === id)
    if (!n) return fail(404, 'No existe esa notificación.')
    n.is_read = isRead
    return reply({ ok: true }, 0)
  }
  return (await api.patch(`/notifications/${id}`, { is_read: isRead })).data
}

// POST /notifications/read-all
export async function markAllRead() {
  if (USE_MOCKS) {
    mias().forEach((n) => { n.is_read = true })
    return reply({ ok: true }, 0)
  }
  return (await api.post('/notifications/read-all')).data
}
