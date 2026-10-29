import api from './api'
import { usaEjemplo } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { usuarioDeLaSesion, esAdmin } from './mocks/sesion'
import { NOTIFICACION } from '../lib/contrato'

// Datos de ejemplo, salvo que esta parte ya esté conectada (VITE_API_REAL).
const USE_MOCKS = usaEjemplo('notificaciones')

// Notificaciones con los campos de la guía: { idNotificacion, tipo, mensaje, leido,
// fechaCreacion, idExperimento } (PARA_VANESA_API.md §3, decisión 5). tipo vale uno de los seis
// de la base ('analisis completado', 'error del pipeline', 'analisis en pausa', 'analisis
// cancelado', 'alerta de disco', 'video borrado'). El título y el enlace los arma el frontend
// con tipo e idExperimento. Sin backend, las que van solo a Administradores llevan soloAdmin.
const mias = () => db.notifications.filter((n) => !n.soloAdmin || esAdmin(usuarioDeLaSesion()))

const TITULO = {
  ANALYSIS_DONE: 'Análisis completado',
  ANALYSIS_FAILED: 'Error en el análisis',
  ANALYSIS_PAUSED: 'Análisis en pausa',
  ANALYSIS_CANCELED: 'Análisis cancelado',
  DISK_ALERT: 'Alerta de disco',
  VIDEO_DELETED: 'Videos borrados',
}

// A dónde lleva cada tipo. La pausa, el error y la cancelación abren el progreso de ese
// experimento: ahí /status dice qué análisis fue (decisión 5).
function enlaceDe(tipo, id) {
  if (tipo === 'DISK_ALERT') return '/admin'
  if (id == null) return '/experimentos'
  if (tipo === 'ANALYSIS_DONE') return `/experimentos/${id}/resultados`
  if (tipo === 'VIDEO_DELETED') return `/experimentos/${id}`
  return '/analisis?experimento=' + id
}

// Notificación de la guía → forma de la campana.
export function deNotificacion(n) {
  const tipo = NOTIFICACION.aFront[n.tipo]
  return {
    id: n.idNotificacion,
    tipo,
    titulo: TITULO[tipo] || 'Aviso',
    texto: n.mensaje,
    enlace: enlaceDe(tipo, n.idExperimento),
    creada: n.fechaCreacion,
    is_read: !!n.leido,
  }
}

// GET /notifications → las de la cuenta, de la más nueva a la más vieja
export async function listNotifications() {
  const d = USE_MOCKS ? await reply(mias(), 60) : (await api.get('/notifications')).data
  return d.map(deNotificacion)
}

// PATCH /notifications/:id { leido }
export async function setRead(id, leido) {
  if (USE_MOCKS) {
    const n = db.notifications.find((x) => x.idNotificacion === id)
    if (!n) return fail(404, 'No existe esa notificación.')
    n.leido = leido
    return reply({}, 0)
  }
  return (await api.patch(`/notifications/${id}`, { leido })).data
}

// GET /notifications/unread-count → { noLeidas }: el total de la cuenta, sin el tope de la lista. Es lo que muestra la campana.
export async function unreadCount() {
  if (USE_MOCKS) return mias().filter((n) => !n.leido).length
  return (await api.get('/notifications/unread-count')).data.noLeidas
}

// «Marcar todas como leídas»: un PATCH por cada no leída. La guía no tiene una ruta para
// todas a la vez, así que no se inventa una (antes se proponía POST /notifications/read-all).
export async function markAllRead() {
  const pendientes = (await listNotifications()).filter((n) => !n.is_read)
  await Promise.all(pendientes.map((n) => setRead(n.id, true)))
  return {}
}
