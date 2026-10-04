import api from './api'
import { USE_MOCKS } from './config'
import { reply } from './mocks/delay'
import * as db from './mocks/data'

// GET /notifications
export async function listNotifications() {
  if (USE_MOCKS) return reply(db.notifications, 60)
  return (await api.get('/notifications')).data
}

// PATCH /notifications/:id → { is_read }
export async function setRead(id, isRead) {
  if (USE_MOCKS) {
    db.notifications.find((n) => n.id === id).is_read = isRead
    return reply({ ok: true }, 0)
  }
  return (await api.patch(`/notifications/${id}`, { is_read: isRead })).data
}

// POST /notifications/read-all
export async function markAllRead() {
  if (USE_MOCKS) {
    db.notifications.forEach((n) => { n.is_read = true })
    return reply({ ok: true }, 0)
  }
  return (await api.post('/notifications/read-all')).data
}
