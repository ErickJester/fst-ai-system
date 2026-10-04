import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// GET /admin/users
export async function listUsers() {
  if (USE_MOCKS) return reply(db.users)
  return (await api.get('/admin/users')).data
}

// POST /admin/users → { nombre, apellidos, email, identificador }; se crea como Investigador
// y el servidor envía la contraseña temporal por correo.
export async function createUser(data) {
  if (USE_MOCKS) {
    const email = data.email.toLowerCase()
    if (db.users.some((u) => u.email === email)) return fail(409, 'Ya existe una cuenta con ese correo.')
    const u = { id: Date.now(), role: 'INVESTIGADOR', is_active: true, ...data, email }
    db.users.push(u)
    return reply(u)
  }
  return (await api.post('/admin/users', data)).data
}

// PATCH /admin/users/:id → { is_active }; el servidor impide dejar el sistema sin administradores
export async function setUserActive(id, isActive) {
  if (USE_MOCKS) {
    const u = db.users.find((x) => x.id === id)
    if (!u) return fail(404, 'No existe esa cuenta.')
    const admins = db.users.filter((x) => x.role === 'ADMIN' && x.is_active)
    if (!isActive && u.role === 'ADMIN' && admins.length <= 1) return fail(409, 'Debe existir al menos un Administrador activo.')
    u.is_active = isActive
    return reply(u)
  }
  return (await api.patch(`/admin/users/${id}`, { is_active: isActive })).data
}

// GET /admin/system → disco, conductas y modelo en uso
export async function getSystem() {
  if (USE_MOCKS) return reply(db.system)
  return (await api.get('/admin/system')).data
}
